package io.tokentrim.cache;

import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.embed.MessageTexts;
import io.tokentrim.embed.TextNormalizer;

import java.util.List;

public final class CacheKeys {

    private CacheKeys() {
    }

    /**
     * Exact cache key: canonical system prefix (capped) plus the latest user turn.
     * Follow-up questions that only make sense with prior turns will miss, which is
     * the safe default. Near-duplicate wording of the same question hits.
     */
    public static String systemKey(List<ChatMessage> messages) {
        return TextNormalizer.canonical(MessageTexts.firstSystem(messages, 400));
    }

    public static String userKey(List<ChatMessage> messages) {
        return TextNormalizer.canonical(MessageTexts.lastUser(messages));
    }

    /**
     * Exact key. The system prefix and the user question are separated by a newline so
     * semantic search can require the same instructions and still compare only the question.
     * A shared system prompt must not drown "capital of France" and "capital of Germany".
     */
    public static String promptKey(List<ChatMessage> messages) {
        return systemKey(messages) + "\n" + userKey(messages);
    }

    /** Empty when the key has no system component. Includes the trailing newline when present. */
    public static String systemPrefix(String promptKey) {
        if (promptKey == null) {
            return "";
        }
        int newline = promptKey.indexOf('\n');
        if (newline < 0) {
            return "";
        }
        return promptKey.substring(0, newline + 1);
    }

    public static String preview(String promptKey) {
        if (promptKey == null) {
            return "";
        }
        return promptKey.length() <= 240 ? promptKey : promptKey.substring(0, 240);
    }
}
