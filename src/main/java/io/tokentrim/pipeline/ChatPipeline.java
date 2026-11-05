package io.tokentrim.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tokentrim.api.TokenTrimHeaders;
import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatCompletionResponse;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.api.dto.TokenTrimOptions;
import io.tokentrim.cache.CacheEntry;
import io.tokentrim.cache.CacheKeys;
import io.tokentrim.cache.CacheMatch;
import io.tokentrim.cache.SemanticCache;
import io.tokentrim.compress.PreparedPrompt;
import io.tokentrim.compress.PromptCompressor;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.EmbeddingModel;
import io.tokentrim.metrics.CostReport;
import io.tokentrim.metrics.SavingsEstimator;
import io.tokentrim.metrics.TokenTrimMetrics;
import io.tokentrim.quality.QualityChecker;
import io.tokentrim.route.ModelRouter;
import io.tokentrim.route.RoutingDecision;
import io.tokentrim.token.TokenCounter;
import io.tokentrim.upstream.UpstreamChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One chat completion: count the naive prompt, compress and trim it, pick a model,
 * return a cached answer when the question is a near duplicate, otherwise call upstream.
 */
public final class ChatPipeline {

    private static final Logger log = LoggerFactory.getLogger(ChatPipeline.class);

    private final TokenTrimProperties properties;
    private final TokenCounter tokenCounter;
    private final PromptCompressor compressor;
    private final EmbeddingModel embeddings;
    private final SemanticCache cache;
    private final ModelRouter router;
    private final UpstreamChatClient upstream;
    private final TokenTrimMetrics metrics;
    private final SavingsEstimator savings;
    private final QualityChecker qualityChecker;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ChatPipeline(
            TokenTrimProperties properties,
            TokenCounter tokenCounter,
            PromptCompressor compressor,
            EmbeddingModel embeddings,
            SemanticCache cache,
            ModelRouter router,
            UpstreamChatClient upstream,
            TokenTrimMetrics metrics,
            SavingsEstimator savings,
            QualityChecker qualityChecker,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.tokenCounter = tokenCounter;
        this.compressor = compressor;
        this.embeddings = embeddings;
        this.cache = cache;
        this.router = router;
        this.upstream = upstream;
        this.metrics = metrics;
        this.savings = savings;
        this.qualityChecker = qualityChecker;
        this.objectMapper = objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public PipelineResult execute(ChatCompletionRequest request) {
        long started = System.nanoTime();
        try {
            PipelineResult result = run(request, started);
            return result;
        } catch (RuntimeException exception) {
            metrics.recordFailure(System.nanoTime() - started);
            throw exception;
        }
    }

    private PipelineResult run(ChatCompletionRequest request, long started) {
        List<ChatMessage> originalMessages = request.getMessages() == null ? List.of() : request.getMessages();
        int originalTokens = tokenCounter.countMessages(originalMessages) + optionDocumentTokens(request.getTokentrim());
        PreparedPrompt prepared = compressor.prepare(originalMessages, request.getTokentrim());
        int compressedTokens = tokenCounter.countMessages(prepared.messages());
        RoutingDecision decision = router.route(
                prepared.messages(),
                request.getModel(),
                request.getTokentrim() == null ? null : request.getTokentrim().getForceModel());

        boolean cacheable = cacheable(request);
        String promptKey = CacheKeys.promptKey(prepared.messages());
        float[] vector = embeddings.embed(CacheKeys.userKey(prepared.messages()));

        if (cacheable) {
            Optional<CacheMatch> hit = cache.find(
                    promptKey,
                    vector,
                    decision.model(),
                    properties.getCache().getSimilarityThreshold());
            if (hit.isPresent()) {
                ChatCompletionResponse cached = readCached(hit.get().entry());
                if (cached != null) {
                    int outputTokens = completionTokens(cached);
                    CostReport cost = savings.estimate(
                            requestedModel(request), decision.model(), originalTokens, 0, outputTokens, true);
                    int saved = Math.max(0, originalTokens);
                    long latency = System.nanoTime() - started;
                    metrics.recordSuccess(true, true, originalTokens, 0, saved, cost.savedUsd(), latency,
                            decision.route(), decision.model());
                    log.info("cache=HIT route={} model={} originalTokens={} sentTokens=0 similarity={}",
                            decision.route(), decision.model(), originalTokens,
                            String.format(Locale.US, "%.3f", hit.get().similarity()));
                    return new PipelineResult(
                            cached,
                            headers(request, decision, true, originalTokens, 0, saved, cost.savedUsd(), latency,
                                    prepared.chunksSelected(), hit.get().similarity(), null),
                            true,
                            originalTokens,
                            0,
                            saved,
                            decision.model(),
                            decision.route(),
                            decision.complexity(),
                            cost.savedUsd(),
                            prepared.chunksSelected());
                }
            }
        }

        ChatCompletionRequest outbound = copyForUpstream(request, prepared.messages(), decision.model());
        long upstreamStarted = System.nanoTime();
        ChatCompletionResponse response = upstream.complete(outbound);
        metrics.recordUpstream(System.nanoTime() - upstreamStarted);

        int outputTokens = completionTokens(response);
        if (cacheable) {
            store(decision.model(), promptKey, vector, response, compressedTokens, outputTokens);
        }

        Double quality = maybeQuality(request, originalMessages, response);
        int sent = compressedTokens;
        int saved = Math.max(0, originalTokens - sent);
        CostReport cost = savings.estimate(
                requestedModel(request), decision.model(), originalTokens, sent, outputTokens, false);
        long latency = System.nanoTime() - started;
        metrics.recordSuccess(cacheable, false, originalTokens, sent, saved, cost.savedUsd(), latency,
                decision.route(), decision.model());
        log.info("cache={} route={} model={} originalTokens={} sentTokens={} savedTokens={} costSavedUsd={}",
                cacheable ? "MISS" : "BYPASS",
                decision.route(),
                decision.model(),
                originalTokens,
                sent,
                saved,
                String.format(Locale.US, "%.6f", cost.savedUsd()));
        return new PipelineResult(
                response,
                headers(request, decision, false, originalTokens, sent, saved, cost.savedUsd(), latency,
                        prepared.chunksSelected(), null, quality),
                false,
                originalTokens,
                sent,
                saved,
                decision.model(),
                decision.route(),
                decision.complexity(),
                cost.savedUsd(),
                prepared.chunksSelected());
    }

    private void store(
            String model,
            String promptKey,
            float[] vector,
            ChatCompletionResponse response,
            int promptTokens,
            int completionTokens
    ) {
        try {
            CacheEntry entry = new CacheEntry(
                    UUID.randomUUID().toString(),
                    model,
                    promptKey,
                    CacheKeys.preview(promptKey),
                    vector,
                    objectMapper.writeValueAsString(response),
                    promptTokens,
                    completionTokens,
                    clock.millis());
            cache.store(entry);
        } catch (JsonProcessingException exception) {
            log.warn("Skipping cache store: {}", exception.getMessage());
        }
    }

    private ChatCompletionResponse readCached(CacheEntry entry) {
        try {
            return objectMapper.readValue(entry.getResponseJson(), ChatCompletionResponse.class);
        } catch (JsonProcessingException exception) {
            log.warn("Skipping unreadable cache entry {}: {}", entry.getId(), exception.getMessage());
            return null;
        }
    }

    private Double maybeQuality(ChatCompletionRequest request, List<ChatMessage> originalMessages, ChatCompletionResponse optimized) {
        if (!shouldCheckQuality(request)) {
            return null;
        }
        try {
            ChatCompletionRequest baseline = copyForUpstream(request, originalMessages, requestedModel(request));
            ChatCompletionResponse baselineResponse = upstream.complete(baseline);
            double score = qualityChecker.similarity(optimized.answerText(), baselineResponse.answerText());
            metrics.recordQuality(score);
            return score;
        } catch (RuntimeException exception) {
            log.warn("Quality check failed: {}", exception.getMessage());
            return null;
        }
    }

    private boolean shouldCheckQuality(ChatCompletionRequest request) {
        TokenTrimOptions options = request.getTokentrim();
        if (options != null && options.isQualityCheck()) {
            return true;
        }
        if (!properties.getQuality().isEnabled()) {
            return false;
        }
        double sample = properties.getQuality().getSampleRate();
        return sample > 0 && ThreadLocalRandom.current().nextDouble() < sample;
    }

    private ChatCompletionRequest copyForUpstream(ChatCompletionRequest request, List<ChatMessage> messages, String model) {
        ChatCompletionRequest copy = objectMapper.convertValue(request, ChatCompletionRequest.class);
        copy.setMessages(messages);
        copy.setModel(model);
        copy.setTokentrim(null);
        copy.setStream(Boolean.FALSE);
        return copy;
    }

    private boolean cacheable(ChatCompletionRequest request) {
        if (!properties.getCache().isEnabled()) {
            return false;
        }
        TokenTrimOptions options = request.getTokentrim();
        if (options != null && options.isSkipCache()) {
            return false;
        }
        if (request.getN() != null && request.getN() > 1) {
            return false;
        }
        if (hasTools(request.getTools())) {
            return false;
        }
        if (request.getMessages() != null) {
            for (ChatMessage message : request.getMessages()) {
                if ("tool".equals(message.getRole())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasTools(JsonNode tools) {
        if (tools == null || tools.isNull()) {
            return false;
        }
        if (tools.isArray()) {
            return !tools.isEmpty();
        }
        if (tools.isObject()) {
            return !tools.isEmpty();
        }
        return true;
    }

    private int optionDocumentTokens(TokenTrimOptions options) {
        if (options == null || options.getDocuments() == null) {
            return 0;
        }
        int extra = 0;
        for (String document : options.getDocuments()) {
            if (document != null && !document.isBlank()) {
                extra += tokenCounter.count(document) + 4;
            }
        }
        return extra;
    }

    private int completionTokens(ChatCompletionResponse response) {
        if (response.getUsage() != null && response.getUsage().getCompletionTokens() != null) {
            return response.getUsage().getCompletionTokens();
        }
        return tokenCounter.count(response.answerText());
    }

    private static String requestedModel(ChatCompletionRequest request) {
        return request.getModel() == null || request.getModel().isBlank() ? "gpt-4o-mini" : request.getModel();
    }

    private HttpHeaders headers(
            ChatCompletionRequest request,
            RoutingDecision decision,
            boolean cacheHit,
            int original,
            int sent,
            int saved,
            double costSaved,
            long latencyNanos,
            int ragChunks,
            Double similarity,
            Double quality
    ) {
        HttpHeaders headers = new HttpHeaders();
        boolean bypass = !cacheable(request);
        headers.add(TokenTrimHeaders.CACHE, bypass ? "BYPASS" : cacheHit ? "HIT" : "MISS");
        headers.add(TokenTrimHeaders.MODEL, decision.model());
        headers.add(TokenTrimHeaders.REQUESTED_MODEL, requestedModel(request));
        headers.add(TokenTrimHeaders.ROUTE, decision.route());
        headers.add(TokenTrimHeaders.COMPLEXITY, String.format(Locale.US, "%.3f", decision.complexity()));
        headers.add(TokenTrimHeaders.TOKENS_ORIGINAL, Integer.toString(original));
        headers.add(TokenTrimHeaders.TOKENS_SENT, Integer.toString(sent));
        headers.add(TokenTrimHeaders.TOKENS_SAVED, Integer.toString(saved));
        headers.add(TokenTrimHeaders.COST_SAVED, String.format(Locale.US, "%.8f", costSaved));
        headers.add(TokenTrimHeaders.LATENCY, Long.toString(latencyNanos / 1_000_000L));
        headers.add(TokenTrimHeaders.RAG_CHUNKS, Integer.toString(ragChunks));
        if (similarity != null) {
            headers.add(TokenTrimHeaders.CACHE_SIMILARITY, String.format(Locale.US, "%.4f", similarity));
        }
        if (quality != null) {
            headers.add(TokenTrimHeaders.QUALITY, String.format(Locale.US, "%.4f", quality));
        }
        return headers;
    }
}
