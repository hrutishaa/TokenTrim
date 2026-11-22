package io.tokentrim.pipeline;

import io.tokentrim.api.dto.ChatCompletionResponse;
import org.springframework.http.HttpHeaders;

public record PipelineResult(
        ChatCompletionResponse response,
        HttpHeaders headers,
        boolean cacheHit,
        int originalTokens,
        int sentTokens,
        int tokensSaved,
        String routedModel,
        String routeName,
        double complexity,
        double costSavedUsd,
        int ragChunks
) {
}
