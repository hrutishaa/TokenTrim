package io.tokentrim.cache;

import io.tokentrim.embed.VectorMath;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * Shared nearest-neighbor scan used by the in-memory cache and by Redis (which stores
 * vectors but does not run vector search unless a module is installed). pgvector does
 * the same comparison inside Postgres.
 */
public final class SemanticMatcher {

    private SemanticMatcher() {
    }

    public static Optional<CacheMatch> best(
            Collection<CacheEntry> entries,
            float[] query,
            String model,
            double threshold,
            Instant now,
            Duration ttl
    ) {
        CacheEntry winner = null;
        double best = -1;
        if (entries == null || query == null) {
            return Optional.empty();
        }
        for (CacheEntry entry : entries) {
            if (entry == null || entry.getEmbedding() == null || entry.getModel() == null) {
                continue;
            }
            if (model != null && !model.equals(entry.getModel())) {
                continue;
            }
            if (expired(entry, now, ttl)) {
                continue;
            }
            if (entry.getEmbedding().length != query.length) {
                continue;
            }
            double similarity = VectorMath.cosine(query, entry.getEmbedding());
            if (similarity >= threshold && similarity > best) {
                best = similarity;
                winner = entry;
            }
        }
        if (winner == null) {
            return Optional.empty();
        }
        return Optional.of(new CacheMatch(winner, best));
    }

    public static boolean expired(CacheEntry entry, Instant now, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || entry == null) {
            return false;
        }
        return entry.createdAt().plus(ttl).isBefore(now);
    }
}
