package io.tokentrim.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySemanticCache implements SemanticCache {

    private final int maxEntries;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, CacheEntry> entries = new HashMap<>();

    public InMemorySemanticCache(int maxEntries, Duration ttl, Clock clock) {
        this.maxEntries = Math.max(1, maxEntries);
        this.ttl = ttl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public synchronized Optional<CacheMatch> find(String promptKey, float[] query, String model, double threshold) {
        evictExpired();
        if (model != null && promptKey != null) {
            for (CacheEntry entry : entries.values()) {
                if (model.equals(entry.getModel()) && promptKey.equals(entry.getPromptKey())) {
                    return Optional.of(new CacheMatch(entry, 1.0));
                }
            }
        }
        return SemanticMatcher.best(sameSystem(promptKey), query, model, threshold, clock.instant(), ttl);
    }

    @Override
    public synchronized void store(CacheEntry entry) {
        entries.values().removeIf(existing ->
                existing.getModel().equals(entry.getModel()) && existing.getPromptKey().equals(entry.getPromptKey()));
        entries.put(entry.getId(), entry);
        while (entries.size() > maxEntries) {
            CacheEntry oldest = entries.values().stream()
                    .min(Comparator.comparingLong(CacheEntry::getCreatedAtEpochMs))
                    .orElse(null);
            if (oldest == null) {
                break;
            }
            entries.remove(oldest.getId());
        }
    }

    @Override
    public synchronized int size() {
        evictExpired();
        return entries.size();
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    private List<CacheEntry> sameSystem(String promptKey) {
        String prefix = CacheKeys.systemPrefix(promptKey);
        if (prefix.isEmpty()) {
            return List.copyOf(entries.values());
        }
        List<CacheEntry> matched = new ArrayList<>();
        for (CacheEntry entry : entries.values()) {
            if (entry.getPromptKey() != null && entry.getPromptKey().startsWith(prefix)) {
                matched.add(entry);
            }
        }
        return matched;
    }

    private void evictExpired() {
        InstantCutoff cutoff = new InstantCutoff(clock.instant(), ttl);
        entries.values().removeIf(cutoff::expired);
    }

    private record InstantCutoff(java.time.Instant now, Duration ttl) {
        boolean expired(CacheEntry entry) {
            return SemanticMatcher.expired(entry, now, ttl);
        }
    }
}
