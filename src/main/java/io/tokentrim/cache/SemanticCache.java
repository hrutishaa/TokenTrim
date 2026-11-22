package io.tokentrim.cache;

import java.util.Optional;

public interface SemanticCache {

    Optional<CacheMatch> find(String promptKey, float[] query, String model, double threshold);

    void store(CacheEntry entry);

    int size();

    void clear();
}
