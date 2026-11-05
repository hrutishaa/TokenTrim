package io.tokentrim.metrics;

public record CostReport(double baselineUsd, double actualUsd, double savedUsd) {
}
