package io.tokentrim.compress;

import io.tokentrim.config.TokenTrimProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoilerplateStripperTest {

    private final BoilerplateStripper stripper = new BoilerplateStripper(
            TokenTrimProperties.createDefault().getCompression().getBoilerplatePhrases());

    @Test
    void stripsFillerButKeepsTheAnswer() {
        String input = "Sure, I'd be happy to help! Paris is the capital.\n\nI hope this helps!";
        String stripped = stripper.strip(input);
        assertThat(stripped.toLowerCase()).doesNotContain("happy to help");
        assertThat(stripped.toLowerCase()).doesNotContain("hope this helps");
        assertThat(stripped).contains("Paris is the capital.");
    }

    @Test
    void leavesFillerInsideCodeFences() {
        String input = """
                Sure, I'd be happy to help!
                ```
                Sure, I'd be happy to help!
                ```
                I hope this helps!""";
        String stripped = stripper.strip(input);
        assertThat(stripped).contains("```\nSure, I'd be happy to help!\n```");
        assertThat(stripped.toLowerCase()).doesNotContain("hope this helps");
    }
}
