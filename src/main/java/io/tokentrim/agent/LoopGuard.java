package io.tokentrim.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses older copies of a repeated tool call and reports when the repeat count
 * inside the window reaches the configured threshold.
 */
public final class LoopGuard {

    private final ObjectMapper mapper;
    private final TokenTrimProperties.LoopGuardSettings settings;

    public LoopGuard(ObjectMapper mapper, TokenTrimProperties.LoopGuardSettings settings) {
        this.mapper = mapper;
        this.settings = settings;
    }

    public Result apply(List<ChatMessage> messages) {
        if (!settings.isEnabled() || messages == null || messages.isEmpty()) {
            return new Result(messages == null ? List.of() : messages, false, 0, false);
        }
        List<Seen> seen = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            for (ToolCalls.Call call : ToolCalls.callsOf(messages.get(i))) {
                seen.add(new Seen(i, call.id(), ToolCalls.signature(call.name(), call.arguments(), mapper), call.name()));
            }
        }
        int window = Math.max(2, settings.getWindow());
        int start = Math.max(0, seen.size() - window);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Integer> latestIndex = new LinkedHashMap<>();
        for (int i = start; i < seen.size(); i++) {
            Seen call = seen.get(i);
            counts.merge(call.signature(), 1, Integer::sum);
            latestIndex.put(call.signature(), i);
        }
        int peak = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int threshold = Math.max(2, settings.getRepeatThreshold());
        boolean detected = peak >= threshold;

        Map<String, Integer> toolMessageIndex = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if ("tool".equals(message.getRole()) && message.getToolCallId() != null) {
                toolMessageIndex.put(message.getToolCallId(), i);
            }
        }

        List<ChatMessage> out = new ArrayList<>(messages);
        for (int i = start; i < seen.size(); i++) {
            Seen call = seen.get(i);
            if (counts.getOrDefault(call.signature(), 0) < 2) {
                continue;
            }
            if (i == latestIndex.get(call.signature())) {
                continue;
            }
            Integer messageIndex = call.id() == null ? null : toolMessageIndex.get(call.id());
            if (messageIndex == null) {
                continue;
            }
            ChatMessage original = out.get(messageIndex);
            String note = "[duplicate] " + call.name() + " repeated with the same arguments; see the latest result";
            out.set(messageIndex, original.withText(note));
        }
        boolean stop = detected && "stop".equalsIgnoreCase(settings.getAction());
        return new Result(out, detected, peak, stop);
    }

    public record Result(List<ChatMessage> messages, boolean detected, int count, boolean stop) {
    }

    private record Seen(int messageIndex, String id, String signature, String name) {
    }
}
