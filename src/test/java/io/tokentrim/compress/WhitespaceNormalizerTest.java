package io.tokentrim.compress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhitespaceNormalizerTest {

    private final WhitespaceNormalizer normalizer = new WhitespaceNormalizer();

    @Test
    void collapsesBlankLinesAndSpacesOutsideCodeFences() {
        String input = "Hello    world\r\n\r\n\r\n```\nprint(1    +  2)\n```\n";
        assertThat(normalizer.normalize(input)).isEqualTo("Hello world\n\n```\nprint(1    +  2)\n```");
    }

    @Test
    void trimsTrailingSpacesOnProse() {
        assertThat(normalizer.normalize("hello   \n\n\nworld  ")).isEqualTo("hello\n\nworld");
    }
}
