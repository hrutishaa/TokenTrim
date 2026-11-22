package io.tokentrim.metrics;

import io.tokentrim.config.TokenTrimProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SavingsEstimatorTest {

    private final SavingsEstimator estimator = new SavingsEstimator(TokenTrimProperties.createDefault());

    @Test
    void countsRoutingAndCompressionAgainstTheRequestedModel() {
        CostReport report = estimator.estimate("gpt-4o", "gpt-4o-mini", 1_000, 200, 50, false);
        assertThat(report.baselineUsd()).isCloseTo(0.003, within(1e-9));
        assertThat(report.actualUsd()).isCloseTo(0.00006, within(1e-9));
        assertThat(report.savedUsd()).isCloseTo(0.00294, within(1e-9));
    }

    @Test
    void cacheHitCostsNothing() {
        CostReport report = estimator.estimate("gpt-4o", "gpt-4o-mini", 1_000, 0, 50, true);
        assertThat(report.actualUsd()).isZero();
        assertThat(report.savedUsd()).isCloseTo(report.baselineUsd(), within(1e-12));
    }
}
