package io.tokentrim.embed;

/**
 * Local feature-hashing embedder. It is deterministic and needs no network, which makes
 * the semantic cache and RAG trimmer usable in tests and in the echo demo.
 * <p>
 * It is a lexical similarity model (words, bigrams, character trigrams), not a neural
 * embedding model. Switch {@code tokentrim.embeddings.provider} to {@code openai} when
 * you want paraphrase quality from a real embedding API.
 */
public final class HashingEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public HashingEmbeddingModel(int dimensions) {
        if (dimensions < 32 || dimensions > 4096) {
            throw new IllegalArgumentException("hashing embedding dimensions must be between 32 and 4096");
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        String canonical = TextNormalizer.canonical(text);
        if (canonical.isBlank()) {
            return vector;
        }
        String[] words = canonical.split(" ");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            add(vector, "w:" + word, 3.0f);
            if (word.length() >= 3) {
                String padded = " " + word + " ";
                for (int i = 0; i + 2 < padded.length(); i++) {
                    add(vector, "c:" + padded.substring(i, i + 3), 0.35f);
                }
            }
        }
        for (int i = 0; i < words.length - 1; i++) {
            add(vector, "b:" + words[i] + "_" + words[i + 1], 0.5f);
        }
        return VectorMath.normalize(vector);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String name() {
        return "hashing-" + dimensions;
    }

    private static void add(float[] vector, String feature, float weight) {
        int hash = feature.hashCode();
        int index = Math.floorMod(hash, vector.length);
        float signed = ((hash >>> 16) & 1) == 0 ? weight : -weight;
        vector[index] += signed;
    }
}
