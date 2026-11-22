package io.tokentrim.rag;

import io.tokentrim.FixturePrompts;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.HashingEmbeddingModel;
import io.tokentrim.token.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagTrimmerTest {

    private final TokenCounter tokenCounter = new TokenCounter("cl100k_base");
    private final RagTrimmer trimmer = new RagTrimmer(
            TokenTrimProperties.createDefault(),
            tokenCounter,
            new HashingEmbeddingModel(256),
            new TextChunker(tokenCounter));

    @Test
    void keepsTheRelevantChunkAndDropsTheRest() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getRag().setTopK(1);
        properties.getRag().setTokenBudget(220);
        RagTrimmer focused = new RagTrimmer(properties, tokenCounter, new HashingEmbeddingModel(256), new TextChunker(tokenCounter));

        RagTrimmer.Selection selection = focused.select(
                List.of(
                        FixturePrompts.franceDocument(),
                        FixturePrompts.germanyDocument(),
                        FixturePrompts.breadDocument()),
                "What is the capital of France?",
                1);

        assertThat(selection.chunks()).hasSize(1);
        assertThat(selection.rendered()).contains("Paris");
        assertThat(selection.rendered()).doesNotContain("Sourdough");
        assertThat(selection.rendered()).doesNotContain("Berlin");
        assertThat(tokenCounter.count(selection.rendered())).isLessThan(
                tokenCounter.count(FixturePrompts.franceDocument())
                        + tokenCounter.count(FixturePrompts.germanyDocument())
                        + tokenCounter.count(FixturePrompts.breadDocument()));
    }

    @Test
    void longTextIsSplitUnderTheChunkBudget() {
        TextChunker chunker = new TextChunker(tokenCounter);
        String text = "Sentence about rivers and rainfall. ".repeat(80);
        List<String> chunks = chunker.chunk(text, 40, 8);
        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(tokenCounter.count(chunk)).isLessThanOrEqualTo(42);
        }
    }
}
