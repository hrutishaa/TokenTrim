package io.tokentrim.agent;

import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.token.TokenCounter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Folds completed tool steps that no longer fit the budget into one extractive progress note.
 * User messages, including the latest one, are copied through unchanged.
 */
public final class StepMemory {

    private final TokenCounter tokenCounter;
    private final TokenTrimProperties.StepMemorySettings settings;

    public StepMemory(TokenCounter tokenCounter, TokenTrimProperties.StepMemorySettings settings) {
        this.tokenCounter = tokenCounter;
        this.settings = settings;
    }

    public List<ChatMessage> fold(List<ChatMessage> messages) {
        if (!settings.isEnabled() || messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        List<Step> steps = steps(messages);
        int recent = Math.max(1, settings.getRecentSteps());
        if (steps.size() <= recent) {
            return messages;
        }
        List<Step> older = steps.subList(0, steps.size() - recent);
        int olderTokens = 0;
        for (Step step : older) {
            for (int index = step.start(); index <= step.end(); index++) {
                olderTokens += tokenCounter.count(messages.get(index).text()) + 4;
            }
        }
        if (olderTokens <= Math.max(64, settings.getTokenBudget())) {
            return messages;
        }
        String note = fit(render(messages, older));
        Set<Integer> drop = new HashSet<>();
        for (Step step : older) {
            for (int index = step.start(); index <= step.end(); index++) {
                drop.add(index);
            }
        }
        List<ChatMessage> out = new ArrayList<>();
        boolean inserted = false;
        for (int i = 0; i < messages.size(); i++) {
            if (drop.contains(i)) {
                if (!inserted) {
                    out.add(ChatMessage.system(note));
                    inserted = true;
                }
                continue;
            }
            out.add(messages.get(i));
        }
        return out;
    }

    private String render(List<ChatMessage> messages, List<Step> older) {
        StringBuilder note = new StringBuilder("Progress so far:");
        int number = 1;
        for (Step step : older) {
            ChatMessage assistant = messages.get(step.start());
            StringBuilder calls = new StringBuilder();
            for (ToolCalls.Call call : ToolCalls.callsOf(assistant)) {
                if (!calls.isEmpty()) {
                    calls.append("; ");
                }
                String args = call.arguments() == null ? "" : call.arguments().replaceAll("\\s+", " ").strip();
                if (args.length() > 120) {
                    args = args.substring(0, 120) + "…";
                }
                calls.append(call.name());
                if (!args.isBlank()) {
                    calls.append(' ').append(args);
                }
            }
            String result = "";
            if (step.end() > step.start()) {
                result = messages.get(step.end()).text().replaceAll("\\s+", " ").strip();
                if (result.length() > 160) {
                    result = result.substring(0, 160) + "…";
                }
            }
            note.append("\n").append(number++).append(". Called ").append(calls);
            if (!result.isBlank()) {
                note.append(" -> ").append(result);
            }
        }
        return note.toString();
    }

    private String fit(String note) {
        int budget = Math.max(64, settings.getTokenBudget());
        String current = note;
        while (tokenCounter.count(current) > budget && current.length() > 80) {
            current = current.substring(0, Math.max(80, (int) (current.length() * 0.75))).strip();
        }
        return current;
    }

    private static List<Step> steps(List<ChatMessage> messages) {
        List<Step> steps = new ArrayList<>();
        int index = 0;
        while (index < messages.size()) {
            ChatMessage message = messages.get(index);
            if (!ToolCalls.callsOf(message).isEmpty()) {
                int end = index;
                int next = index + 1;
                while (next < messages.size() && "tool".equals(messages.get(next).getRole())) {
                    end = next;
                    next++;
                }
                if (end > index) {
                    steps.add(new Step(index, end));
                }
                index = next;
                continue;
            }
            index++;
        }
        return steps;
    }

    private record Step(int start, int end) {
    }
}
