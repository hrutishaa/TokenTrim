package io.tokentrim.compress;

import io.tokentrim.api.dto.ChatMessage;

import java.util.List;

public record PreparedPrompt(
        List<ChatMessage> messages,
        int documentsConsidered,
        int chunksSelected
) {
}
