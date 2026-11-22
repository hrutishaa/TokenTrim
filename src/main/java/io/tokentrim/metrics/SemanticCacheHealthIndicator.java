package io.tokentrim.metrics;

import io.tokentrim.cache.SemanticCache;
import io.tokentrim.config.TokenTrimProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SemanticCacheHealthIndicator implements HealthIndicator {

    private final SemanticCache cache;
    private final TokenTrimProperties properties;

    public SemanticCacheHealthIndicator(SemanticCache cache, TokenTrimProperties properties) {
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            return Health.up()
                    .withDetail("backend", properties.getCache().getBackend())
                    .withDetail("entries", cache.size())
                    .build();
        } catch (RuntimeException exception) {
            return Health.down(exception)
                    .withDetail("backend", properties.getCache().getBackend())
                    .build();
        }
    }
}
