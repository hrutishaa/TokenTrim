package io.tokentrim.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatsSnapshot(
        long requests,
        @JsonProperty("cache_hits") long cacheHits,
        @JsonProperty("cache_misses") long cacheMisses,
        @JsonProperty("cache_bypasses") long cacheBypasses,
        @JsonProperty("cache_hit_rate") double cacheHitRate,
        @JsonProperty("tokens_original") long tokensOriginal,
        @JsonProperty("tokens_sent") long tokensSent,
        @JsonProperty("tokens_saved") long tokensSaved,
        @JsonProperty("estimated_cost_saved_usd") double estimatedCostSavedUsd,
        @JsonProperty("average_latency_ms") double averageLatencyMs,
        @JsonProperty("upstream_errors") long upstreamErrors
) {
}
