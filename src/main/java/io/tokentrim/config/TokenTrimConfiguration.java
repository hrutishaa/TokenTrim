package io.tokentrim.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.tokentrim.cache.InMemorySemanticCache;
import io.tokentrim.cache.PgVectorSemanticCache;
import io.tokentrim.cache.RedisSemanticCache;
import io.tokentrim.cache.SemanticCache;
import io.tokentrim.embed.EmbeddingModel;
import io.tokentrim.metrics.TokenTrimMetrics;
import io.tokentrim.pipeline.ChatPipeline;
import io.tokentrim.token.TokenCounter;
import io.tokentrim.upstream.UpstreamChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TokenTrimConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TokenTrimConfiguration.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TokenCounter tokenCounter(TokenTrimProperties properties) {
        return TokenTrimFactory.tokenCounter(properties);
    }

    @Bean
    EmbeddingModel embeddingModel(TokenTrimProperties properties) {
        return TokenTrimFactory.embeddingModel(properties);
    }

    @Bean
    UpstreamChatClient upstreamChatClient(TokenTrimProperties properties, TokenCounter tokenCounter) {
        return TokenTrimFactory.upstream(properties, tokenCounter);
    }

    @Bean
    @ConditionalOnProperty(name = "tokentrim.cache.backend", havingValue = "memory", matchIfMissing = true)
    SemanticCache inMemorySemanticCache(TokenTrimProperties properties, Clock clock) {
        return new InMemorySemanticCache(
                properties.getCache().getMaxEntries(),
                properties.getCache().getTtl(),
                clock);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "tokentrim.cache.backend", havingValue = "redis")
    SemanticCache redisSemanticCache(TokenTrimProperties properties, ObjectMapper objectMapper, Clock clock) {
        TokenTrimProperties.Redis redis = properties.getCache().getRedis();
        return new RedisSemanticCache(
                redis.getHost(),
                redis.getPort(),
                redis.getPassword(),
                objectMapper,
                properties.getCache().getMaxEntries(),
                properties.getCache().getTtl(),
                clock);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "tokentrim.cache.backend", havingValue = "pgvector")
    SemanticCache pgVectorSemanticCache(TokenTrimProperties properties, Clock clock) {
        TokenTrimProperties.PgVector pgvector = properties.getCache().getPgvector();
        return new PgVectorSemanticCache(
                pgvector.getUrl(),
                pgvector.getUsername(),
                pgvector.getPassword(),
                properties.getEmbeddings().getDimensions(),
                properties.getCache().getTtl(),
                clock);
    }

    @Bean
    TokenTrimMetrics tokenTrimMetrics(MeterRegistry meterRegistry) {
        return new TokenTrimMetrics(meterRegistry);
    }

    @Bean
    ChatPipeline chatPipeline(
            TokenTrimProperties properties,
            SemanticCache semanticCache,
            UpstreamChatClient upstreamChatClient,
            EmbeddingModel embeddingModel,
            TokenCounter tokenCounter,
            TokenTrimMetrics tokenTrimMetrics,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        return new ChatPipeline(
                properties,
                tokenCounter,
                TokenTrimFactory.promptCompressor(properties, tokenCounter, embeddingModel),
                embeddingModel,
                semanticCache,
                new io.tokentrim.route.ModelRouter(properties, new io.tokentrim.route.ComplexityScorer(tokenCounter)),
                upstreamChatClient,
                tokenTrimMetrics,
                new io.tokentrim.metrics.SavingsEstimator(properties),
                new io.tokentrim.quality.QualityChecker(embeddingModel),
                objectMapper,
                clock);
    }

    @Bean
    ApplicationRunner startupLogger(TokenTrimProperties properties, EmbeddingModel embeddingModel) {
        return args -> log.info(
                "TokenTrim ready. upstreamMode={} cache={} embeddings={} routing={} tokenizer={}",
                properties.getUpstream().getMode(),
                properties.getCache().getBackend(),
                embeddingModel.name(),
                properties.getRouting().isEnabled(),
                properties.getTokenizer().getEncoding());
    }
}
