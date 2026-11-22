package io.tokentrim.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySemanticCacheTest {

    @Test
    void exactKeyHitsEvenWhenVectorsWouldMiss() {
        InMemorySemanticCache cache = new InMemorySemanticCache(10, Duration.ofHours(1), Clock.systemUTC());
        cache.store(entry("capital france", new float[]{1f, 0f, 0f}));

        assertThat(cache.find("capital france", new float[]{0f, 1f, 0f}, "gpt-4o-mini", 0.99))
                .get()
                .extracting(CacheMatch::similarity)
                .isEqualTo(1.0);
    }

    @Test
    void semanticHitAndMiss() {
        InMemorySemanticCache cache = new InMemorySemanticCache(10, Duration.ofHours(1), Clock.systemUTC());
        cache.store(entry("capital france", new float[]{1f, 0f, 0f}));

        assertThat(cache.find("other", new float[]{0.99f, 0.01f, 0f}, "gpt-4o-mini", 0.9)).isPresent();
        assertThat(cache.find("other", new float[]{0f, 1f, 0f}, "gpt-4o-mini", 0.9)).isEmpty();
    }

    @Test
    void ttlEvictsEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemorySemanticCache cache = new InMemorySemanticCache(10, Duration.ofMinutes(5), clock);
        cache.store(new CacheEntry(
                "id-ttl",
                "gpt-4o-mini",
                "capital france",
                "capital france",
                new float[]{1f, 0f},
                "{\"ok\":true}",
                3,
                4,
                clock.instant().toEpochMilli()));
        assertThat(cache.size()).isEqualTo(1);
        clock.instant = clock.instant.plus(Duration.ofMinutes(10));
        assertThat(cache.find("capital france", new float[]{1f, 0f}, "gpt-4o-mini", 0.5)).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void entryJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CacheEntry entry = entry("capital france", new float[]{0.2f, 0.8f});
        CacheEntry restored = mapper.readValue(mapper.writeValueAsString(entry), CacheEntry.class);
        assertThat(restored.getPromptKey()).isEqualTo("capital france");
        assertThat(restored.getEmbedding()).containsExactly(0.2f, 0.8f);
        assertThat(restored.getResponseJson()).isEqualTo("{\"ok\":true}");
    }

    private static CacheEntry entry(String key, float[] vector) {
        return new CacheEntry(
                "id-" + key,
                "gpt-4o-mini",
                key,
                key,
                vector,
                "{\"ok\":true}",
                3,
                4,
                Instant.now().toEpochMilli());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
