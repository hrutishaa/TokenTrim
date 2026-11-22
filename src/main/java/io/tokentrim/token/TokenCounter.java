package io.tokentrim.token;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import io.tokentrim.api.dto.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Counts tokens with JTokkit. Message totals use OpenAI's published chat overhead
 * (about 4 tokens per message plus a small priming constant) so savings math is stable
 * even when the upstream usage field is missing.
 */
public final class TokenCounter {

    private final Encoding encoding;
    private final String encodingName;

    public TokenCounter(String encodingName) {
        this.encodingName = encodingName == null ? "cl100k_base" : encodingName;
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(parse(this.encodingName));
    }

    public String encodingName() {
        return encodingName;
    }

    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    public int countMessages(List<ChatMessage> messages) {
        int total = 3;
        if (messages == null) {
            return total;
        }
        for (ChatMessage message : messages) {
            total += 4;
            total += count(message.getRole());
            total += count(message.text());
            if (message.getName() != null) {
                total += count(message.getName()) + 1;
            }
        }
        return total;
    }

    /**
     * Splits {@code text} into windows of at most {@code size} tokens, overlapping by
     * {@code overlap} tokens. Used by RAG chunking.
     */
    public List<String> windows(String text, int size, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int window = Math.max(8, size);
        int overlapTokens = Math.max(0, Math.min(overlap, window - 1));
        IntArrayList ids = encoding.encode(text);
        if (ids.isEmpty()) {
            return List.of();
        }
        int step = Math.max(1, window - overlapTokens);
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += step) {
            int end = Math.min(ids.size(), start + window);
            IntArrayList slice = new IntArrayList(end - start);
            for (int i = start; i < end; i++) {
                slice.add(ids.get(i));
            }
            String decoded = encoding.decode(slice).strip();
            if (!decoded.isEmpty()) {
                pieces.add(decoded);
            }
            if (end == ids.size()) {
                break;
            }
        }
        return pieces;
    }

    private static EncodingType parse(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "o200k_base" -> EncodingType.O200K_BASE;
            case "p50k_base" -> EncodingType.P50K_BASE;
            case "p50k_edit" -> EncodingType.P50K_EDIT;
            case "r50k_base" -> EncodingType.R50K_BASE;
            default -> EncodingType.CL100K_BASE;
        };
    }
}
