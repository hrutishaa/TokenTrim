package io.tokentrim.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record RunSnapshot(
        String id,
        long requests,
        @JsonProperty("tokens_original") long tokensOriginal,
        @JsonProperty("tokens_sent") long tokensSent,
        @JsonProperty("tokens_saved") long tokensSaved,
        @JsonProperty("tokens_saved_by_technique") Map<String, Long> tokensSavedByTechnique,
        @JsonProperty("soft_limit_tokens") long softLimitTokens,
        @JsonProperty("hard_limit_tokens") long hardLimitTokens,
        @JsonProperty("over_soft_limit") boolean overSoftLimit,
        @JsonProperty("updated_at_ms") long updatedAtMs
) {
}
