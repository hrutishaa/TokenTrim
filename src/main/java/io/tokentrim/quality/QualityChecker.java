package io.tokentrim.quality;

import io.tokentrim.embed.EmbeddingModel;
import io.tokentrim.embed.VectorMath;

/**
 * Compares an optimized completion with a baseline completion of the untouched prompt.
 * The score is cosine similarity of the two answers. It is a cheap proxy for "did compression
 * change the answer", not a human eval.
 */
public final class QualityChecker {

    private final EmbeddingModel embeddings;

    public QualityChecker(EmbeddingModel embeddings) {
        this.embeddings = embeddings;
    }

    public double similarity(String optimized, String baseline) {
        String left = optimized == null ? "" : optimized.strip();
        String right = baseline == null ? "" : baseline.strip();
        if (left.equals(right)) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        return VectorMath.cosine(embeddings.embed(left), embeddings.embed(right));
    }
}
