package io.tokentrim.embed;

import io.tokentrim.api.dto.ChatMessage;

import java.util.List;

public final class MessageTexts {

    private MessageTexts() {
    }

    public static String lastUser(List<ChatMessage> messages) {
        String last = "";
        if (messages == null) {
            return last;
        }
        for (ChatMessage message : messages) {
            if ("user".equals(message.getRole())) {
                last = message.text();
            }
        }
        return last;
    }

    public static String firstSystem(List<ChatMessage> messages, int maxChars) {
        if (messages == null) {
            return "";
        }
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                String text = message.text();
                if (text.length() > maxChars) {
                    return text.substring(0, maxChars);
                }
                return text;
            }
        }
        return "";
    }

    public static String join(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(message.getRole() == null ? "" : message.getRole());
            builder.append(": ");
            builder.append(message.text());
        }
        return builder.toString();
    }
}
