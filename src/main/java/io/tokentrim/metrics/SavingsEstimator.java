package io.tokentrim.metrics;

import io.tokentrim.config.TokenTrimProperties;

/**
 * Compares the cost of sending the untouched prompt to the requested model with the
 * cost of what TokenTrim actually did. Prices are configurable estimates.
 * A cache hit has zero marginal cost. A quality-check call is not included; it is extra eval spend.
 */
public final class SavingsEstimator {

    private final TokenTrimProperties properties;

    public SavingsEstimator(TokenTrimProperties properties) {
        this.properties = properties;
    }

    public CostReport estimate(
            String requestedModel,
            String routedModel,
            int naiveInputTokens,
            int sentInputTokens,
            int outputTokens,
            boolean cacheHit
    ) {
        TokenTrimProperties.ModelPrice requested = priceFor(requestedModel);
        TokenTrimProperties.ModelPrice routed = priceFor(routedModel);
        double baseline = cost(naiveInputTokens, requested.getInputPerMillion())
                + cost(outputTokens, requested.getOutputPerMillion());
        double actual = cacheHit
                ? 0d
                : cost(sentInputTokens, routed.getInputPerMillion())
                + cost(outputTokens, routed.getOutputPerMillion());
        return new CostReport(baseline, actual, Math.max(0d, baseline - actual));
    }

    public TokenTrimProperties.ModelPrice priceFor(String model) {
        var models = properties.getPricing().getModels();
        if (model != null && models.containsKey(model)) {
            return models.get(model);
        }
        if (models.containsKey("default")) {
            return models.get("default");
        }
        return new TokenTrimProperties.ModelPrice(0.50, 1.50);
    }

    private static double cost(int tokens, double perMillion) {
        return tokens * perMillion / 1_000_000d;
    }
}
