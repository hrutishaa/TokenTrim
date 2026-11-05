package io.tokentrim;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.tokentrim.cache.InMemorySemanticCache;
import io.tokentrim.config.TokenTrimFactory;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.EmbeddingModel;
import io.tokentrim.metrics.TokenTrimMetrics;
import io.tokentrim.pipeline.ChatPipeline;
import io.tokentrim.token.TokenCounter;
import io.tokentrim.upstream.EchoUpstreamChatClient;

import java.time.Clock;

public final class TestRig {

    public final TokenTrimProperties properties;
    public final TokenCounter tokenCounter;
    public final EmbeddingModel embeddings;
    public final InMemorySemanticCache cache;
    public final CountingUpstream upstream;
    public final TokenTrimMetrics metrics;
    public final ChatPipeline pipeline;
    public final ObjectMapper objectMapper;

    public TestRig() {
        this(TokenTrimProperties.createDefault());
    }

    public TestRig(TokenTrimProperties properties) {
        this.properties = properties;
        this.tokenCounter = TokenTrimFactory.tokenCounter(properties);
        this.embeddings = TokenTrimFactory.embeddingModel(properties);
        this.cache = new InMemorySemanticCache(properties.getCache().getMaxEntries(), properties.getCache().getTtl(), Clock.systemUTC());
        this.upstream = new CountingUpstream(new EchoUpstreamChatClient(tokenCounter));
        this.metrics = new TokenTrimMetrics(new SimpleMeterRegistry());
        this.objectMapper = new ObjectMapper();
        this.pipeline = new ChatPipeline(
                properties,
                tokenCounter,
                TokenTrimFactory.promptCompressor(properties, tokenCounter, embeddings),
                embeddings,
                cache,
                new io.tokentrim.route.ModelRouter(properties, new io.tokentrim.route.ComplexityScorer(tokenCounter)),
                upstream,
                metrics,
                new io.tokentrim.metrics.SavingsEstimator(properties),
                new io.tokentrim.quality.QualityChecker(embeddings),
                objectMapper,
                Clock.systemUTC());
    }
}
