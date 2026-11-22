package io.tokentrim.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.EmbeddingModel;
import io.tokentrim.token.TokenCounter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the agent-mode passes in order: compact tool output, collapse repeated calls,
 * fold old steps, then prune the tool list.
 */
public final class AgentPreprocessor {

    private final TokenTrimProperties properties;
    private final TokenCounter tokenCounter;
    private final ToolOutputCompactor compactor;
    private final LoopGuard loopGuard;
    private final StepMemory stepMemory;
    private final ToolSchemaPruner schemaPruner;

    public AgentPreprocessor(
            TokenTrimProperties properties,
            TokenCounter tokenCounter,
            EmbeddingModel embeddings,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.tokenCounter = tokenCounter;
        this.compactor = new ToolOutputCompactor(tokenCounter, objectMapper, properties.getAgent().getToolCompaction());
        this.loopGuard = new LoopGuard(objectMapper, properties.getAgent().getLoopGuard());
        this.stepMemory = new StepMemory(tokenCounter, properties.getAgent().getStepMemory());
        this.schemaPruner = new ToolSchemaPruner(
                embeddings, tokenCounter, objectMapper, properties.getAgent().getSchemaPruning());
    }

    public AgentOutcome apply(List<ChatMessage> messages, JsonNode tools, JsonNode toolChoice) {
        List<ChatMessage> original = messages == null ? List.of() : messages;
        List<ChatMessage> current = original;
        Map<String, Integer> saved = new LinkedHashMap<>();
        saved.put(AgentOutcome.TOOL_COMPACTION, 0);
        saved.put(AgentOutcome.LOOP_DEDUPE, 0);
        saved.put(AgentOutcome.STEP_MEMORY, 0);
        saved.put(AgentOutcome.SCHEMA_PRUNING, 0);
        boolean loopDetected = false;
        int loopCount = 0;
        boolean stop = false;
        JsonNode prunedTools = tools;

        if (!properties.getAgent().isEnabled()) {
            return new AgentOutcome(current, prunedTools, saved, false, 0, false);
        }

        int before = tokenCounter.countMessages(current);
        current = compactor.compact(current);
        saved.put(AgentOutcome.TOOL_COMPACTION, Math.max(0, before - tokenCounter.countMessages(current)));

        before = tokenCounter.countMessages(current);
        LoopGuard.Result loop = loopGuard.apply(current);
        current = loop.messages();
        loopDetected = loop.detected();
        loopCount = loop.count();
        stop = loop.stop();
        saved.put(AgentOutcome.LOOP_DEDUPE, Math.max(0, before - tokenCounter.countMessages(current)));

        before = tokenCounter.countMessages(current);
        current = stepMemory.fold(current);
        saved.put(AgentOutcome.STEP_MEMORY, Math.max(0, before - tokenCounter.countMessages(current)));

        int toolsBefore = countTools(tools);
        prunedTools = schemaPruner.prune(tools, current, toolChoice, original);
        saved.put(AgentOutcome.SCHEMA_PRUNING, Math.max(0, toolsBefore - countTools(prunedTools)));

        return new AgentOutcome(current, prunedTools, Map.copyOf(saved), loopDetected, loopCount, stop);
    }

    private int countTools(JsonNode tools) {
        if (tools == null || tools.isNull()) {
            return 0;
        }
        return tokenCounter.count(tools.toString());
    }
}
