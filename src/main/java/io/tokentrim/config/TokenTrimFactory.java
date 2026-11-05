package io.tokentrim.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.tokentrim.cache.SemanticCache;
import io.tokentrim.compress.BoilerplateStripper;
import io.tokentrim.compress.DocumentExtractor;
import io.tokentrim.compress.HistoryBudgetTrimmer;
import io.tokentrim.compress.PromptCompressor;
import io.tokentrim.compress.WhitespaceNormalizer;
import io.tokentrim.embed.EmbeddingModel;
import io.tokentrim.embed.HashingEmbeddingModel;
import io.tokentrim.embed.OpenAiEmbeddingModel;
import io.tokentrim.metrics.SavingsEstimator;
import io.tokentrim.metrics.TokenTrimMetrics;
import io.tokentrim.pipeline.ChatPipeline;
import io.tokentrim.quality.QualityChecker;
import io.tokentrim.rag.RagTrimmer;
import io.tokentrim.rag.TextChunker;
import io.tokentrim.route.ComplexityScorer;
import io.tokentrim.route.ModelRouter;
import io.tokentrim.token.TokenCounter;
import io.tokentrim.upstream.EchoUpstreamChatClient;
import io.tokentrim.upstream.HttpUpstreamChatClient;
import io.tokentrim.upstream.UpstreamChatClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

public final class TokenTrimFactory {

    private TokenTrimFactory() {
    }

    public static TokenCounter tokenCounter(TokenTrimProperties properties) {
        return new TokenCounter(properties.getTokenizer().getEncoding());
    }

    public static EmbeddingModel embeddingModel(TokenTrimProperties properties) {
        String provider = properties.getEmbeddings().getProvider();
        if (provider != null && provider.equalsIgnoreCase("openai")) {
            return new OpenAiEmbeddingModel(
                    restClient(properties),
                    properties.getUpstream().getApiKey(),
                    properties.getEmbeddings().getModel(),
                    properties.getEmbeddings().getDimensions());
        }
        return new HashingEmbeddingModel(properties.getEmbeddings().getDimensions());
    }

    public static PromptCompressor promptCompressor(
            TokenTrimProperties properties,
            TokenCounter tokenCounter,
            EmbeddingModel embeddings
    ) {
        return new PromptCompressor(
                properties,
                new WhitespaceNormalizer(),
                new BoilerplateStripper(properties.getCompression().getBoilerplatePhrases()),
                new HistoryBudgetTrimmer(tokenCounter, properties.getCompression().getMaxSummaryTokens()),
                new DocumentExtractor(),
                new RagTrimmer(properties, tokenCounter, embeddings, new TextChunker(tokenCounter)));
    }

    public static UpstreamChatClient upstream(TokenTrimProperties properties, TokenCounter tokenCounter) {
        if ("echo".equalsIgnoreCase(properties.getUpstream().getMode())) {
            return new EchoUpstreamChatClient(tokenCounter);
        }
        return new HttpUpstreamChatClient(restClient(properties), properties.getUpstream().getApiKey());
    }

    public static ChatPipeline pipeline(
            TokenTrimProperties properties,
            SemanticCache cache,
            UpstreamChatClient upstream,
            EmbeddingModel embeddings,
            TokenCounter tokenCounter,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        return new ChatPipeline(
                properties,
                tokenCounter,
                promptCompressor(properties, tokenCounter, embeddings),
                embeddings,
                cache,
                new ModelRouter(properties, new ComplexityScorer(tokenCounter)),
                upstream,
                new TokenTrimMetrics(meterRegistry),
                new SavingsEstimator(properties),
                new QualityChecker(embeddings),
                objectMapper,
                clock);
    }

    public static RestClient restClient(TokenTrimProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getUpstream().getConnectTimeout());
        factory.setReadTimeout(properties.getUpstream().getReadTimeout());
        String baseUrl = properties.getUpstream().getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
