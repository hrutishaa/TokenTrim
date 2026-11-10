package io.tokentrim.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.tokentrim.api.dto.ChatMessage;

import java.util.List;
import java.util.Map;

public record AgentOutcome(
        List<ChatMessage> messages,
        JsonNode tools,
        Map<String, Integer> tokensSaved,
        boolean loopDetected,
        int loopCount,
        boolean stopForLoop
) {
    public static final String TOOL_COMPACTION = "tool_compaction";
    public static final String SCHEMA_PRUNING = "schema_pruning";
    public static final String LOOP_DEDUPE = "loop_dedupe";
    public static final String STEP_MEMORY = "step_memory";

    public int saved(String technique) {
        return tokensSaved.getOrDefault(technique, 0);
    }
}
