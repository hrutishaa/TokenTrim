package io.tokentrim.pipeline;

import io.tokentrim.FixturePrompts;
import io.tokentrim.TestRig;
import io.tokentrim.api.TokenTrimHeaders;
import io.tokentrim.config.TokenTrimProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproducible savings check. Run with
 * {@code ./mvnw -Dtest=TokenSavingsBenchmarkTest -Dsurefire.useFile=false test}
 * to print the report.
 */
class TokenSavingsBenchmarkTest {

    @Test
    void verboseFixtureSavesAtLeastHalfTheTokensAndTheRepeatIsFree() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getCompression().setHistoryTokenBudget(512);
        properties.getRag().setTopK(2);
        properties.getRag().setTokenBudget(320);
        TestRig rig = new TestRig(properties);

        PipelineResult first = rig.pipeline.execute(FixturePrompts.verboseEasy());
        PipelineResult second = rig.pipeline.execute(FixturePrompts.verboseEasy());
        PipelineResult hard = rig.pipeline.execute(FixturePrompts.hard());

        double ratio = first.tokensSaved() / (double) first.originalTokens();
        String report = """
                TokenTrim benchmark
                original tokens : %d
                sent tokens     : %d
                tokens saved    : %d (%.1f%%)
                estimated USD   : %.6f
                route           : %s -> %s
                rag chunks      : %d
                repeat cache    : %s
                upstream calls  : %d
                hard route      : %s -> %s
                """.formatted(
                first.originalTokens(),
                first.sentTokens(),
                first.tokensSaved(),
                ratio * 100,
                first.costSavedUsd(),
                first.routeName(),
                first.routedModel(),
                first.ragChunks(),
                second.headers().getFirst(TokenTrimHeaders.CACHE),
                rig.upstream.calls(),
                hard.routeName(),
                hard.routedModel());
        System.out.println(report);

        assertThat(ratio)
                .as("token savings ratio%n%s", report)
                .isGreaterThanOrEqualTo(0.50);
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.sentTokens()).isZero();
        assertThat(first.routedModel()).isEqualTo("gpt-4o-mini");
        assertThat(hard.routedModel()).isEqualTo("gpt-4o");
        assertThat(rig.upstream.calls()).isEqualTo(2);
    }
}
