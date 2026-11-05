package io.tokentrim.compress;

import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.token.TokenCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the system prompt and the newest turns that fit in a token budget.
 * Older turns are replaced with a short extractive summary. The latest message is never dropped,
 * even when it alone exceeds the budget.
 */
public final class HistoryBudgetTrimmer {

    private final TokenCounter tokenCounter;
    private final int maxSummaryTokens;

    public HistoryBudgetTrimmer(TokenCounter tokenCounter, int maxSummaryTokens) {
        this.tokenCounter = tokenCounter;
        this.maxSummaryTokens = Math.max(32, maxSummaryTokens);
    }

    public List<ChatMessage> trim(List<ChatMessage> messages, int budget) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(32, budget);
        List<ChatMessage> system = new ArrayList<>();
        List<ChatMessage> dialogue = new ArrayList<>();
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                system.add(message);
            } else {
                dialogue.add(message);
            }
        }
        if (dialogue.isEmpty()) {
            return List.copyOf(messages);
        }

        List<ChatMessage> kept = new ArrayList<>();
        kept.add(dialogue.get(dialogue.size() - 1));
        List<ChatMessage> dropped = new ArrayList<>();
        for (int i = dialogue.size() - 2; i >= 0; i--) {
            ChatMessage candidate = dialogue.get(i);
            if (!candidate.isPlainText()) {
                kept.add(0, candidate);
                continue;
            }
            List<ChatMessage> trialDialogue = new ArrayList<>();
            trialDialogue.add(candidate);
            trialDialogue.addAll(kept);
            int cost = tokenCounter.countMessages(combine(system, trialDialogue));
            if (cost <= limit) {
                kept.add(0, candidate);
            } else {
                dropped.add(0, candidate);
            }
        }
        if (dropped.isEmpty()) {
            return combine(system, kept);
        }

        String summary = fitSummary(dropped);
        if (summary.isBlank()) {
            return combine(system, kept);
        }
        ChatMessage summaryMessage = ChatMessage.system("Earlier conversation, condensed:\n" + summary);
        while (!summary.isBlank() && tokenCounter.countMessages(combine(system, List.of(summaryMessage), kept)) > limit) {
            if (summary.length() < 40) {
                summary = "";
                break;
            }
            summary = summary.substring(0, Math.max(40, (int) (summary.length() * 0.75))).strip();
            summaryMessage = ChatMessage.system("Earlier conversation, condensed:\n" + summary);
        }
        if (summary.isBlank()) {
            return combine(system, kept);
        }
        List<ChatMessage> withSummary = combine(system, List.of(summaryMessage), kept);
        if (tokenCounter.countMessages(withSummary) <= limit) {
            return withSummary;
        }
        return combine(system, kept);
    }

    private String fitSummary(List<ChatMessage> dropped) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : dropped) {
            String role = message.getRole() == null ? "message" : message.getRole();
            String text = message.text().replaceAll("\\s+", " ").strip();
            if (text.length() > 180) {
                text = text.substring(0, 180).strip() + "...";
            }
            if (text.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(role).append(": ").append(text);
        }
        String summary = builder.toString().strip();
        while (!summary.isEmpty() && tokenCounter.count(summary) > maxSummaryTokens && summary.length() > 40) {
            summary = summary.substring(0, Math.max(40, (int) (summary.length() * 0.8))).strip();
        }
        return summary;
    }

    @SafeVarargs
    private static List<ChatMessage> combine(List<ChatMessage>... parts) {
        List<ChatMessage> combined = new ArrayList<>();
        for (List<ChatMessage> part : parts) {
            combined.addAll(part);
        }
        return combined;
    }
}
