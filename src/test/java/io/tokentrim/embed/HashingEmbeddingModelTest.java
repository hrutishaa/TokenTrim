package io.tokentrim.embed;

import io.tokentrim.config.TokenTrimProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel(256);
    private final double threshold = TokenTrimProperties.createDefault().getCache().getSimilarityThreshold();

    @Test
    void nearDuplicatesOutrankUnrelatedAndCrossTheCacheThreshold() {
        float[] france = model.embed("What is the capital of France?");
        double same = VectorMath.cosine(france, model.embed("What's the capital of France?"));
        double paraphrase = VectorMath.cosine(france, model.embed("Tell me the capital city of France"));
        double germany = VectorMath.cosine(france, model.embed("What is the capital of Germany?"));
        double bread = VectorMath.cosine(france, model.embed("How do I bake sourdough bread?"));

        System.out.printf(
                "embedding scores same=%.3f paraphrase=%.3f germany=%.3f bread=%.3f threshold=%.2f%n",
                same, paraphrase, germany, bread, threshold);
        assertThat(same).isGreaterThan(0.99);
        assertThat(paraphrase).isGreaterThan(germany);
        assertThat(paraphrase).isGreaterThan(bread);
        assertThat(germany).isLessThan(threshold);
        assertThat(bread).isLessThan(threshold);
        assertThat(paraphrase).isGreaterThan(threshold);
    }

    @Test
    void canonicalFormCollapsesContractionsAndStopwords() {
        assertThat(TextNormalizer.canonical("What's the capital of France?"))
                .isEqualTo(TextNormalizer.canonical("What is the capital of France?"));
    }
}
