package io.tokentrim.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticMatcherTest {

    @Test
    void hitsAboveThresholdAndMissesOrthogonalVectors() {
        float[] stored = {1f, 0f, 0f};
        float[] orthogonal = {0f, 1f, 0f};
        CacheEntry entry = entry(stored, "gpt-4o-mini", "capital france");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(SemanticMatcher.best(List.of(entry), stored, "gpt-4o-mini", 0.9, now, Duration.ofHours(1)))
                .isPresent();
        assertThat(SemanticMatcher.best(List.of(entry), orthogonal, "gpt-4o-mini", 0.9, now, Duration.ofHours(1)))
                .isEmpty();
        assertThat(SemanticMatcher.best(List.of(entry), stored, "gpt-4o", 0.9, now, Duration.ofHours(1)))
                .isEmpty();
    }

    @Test
    void expiredEntriesMiss() {
        float[] stored = {1f, 0f};
        CacheEntry entry = new CacheEntry(
                "id", "gpt-4o-mini", "key", "key", stored, "{}", 1, 1,
                Instant.parse("2026-01-01T00:00:00Z").toEpochMilli());
        Instant later = Instant.parse("2026-01-02T00:00:00Z");
        assertThat(SemanticMatcher.best(List.of(entry), stored, "gpt-4o-mini", 0.5, later, Duration.ofHours(1)))
                .isEmpty();
    }

    private static CacheEntry entry(float[] vector, String model, String key) {
        return new CacheEntry("id", model, key, key, vector, "{}", 1, 1, Instant.now().toEpochMilli());
    }
}
