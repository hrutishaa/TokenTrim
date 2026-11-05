package io.tokentrim.rag;

import io.tokentrim.token.TokenCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs paragraphs into token windows. A paragraph that is already over the budget is
 * split with the tokenizer so every chunk stays within {@code chunkTokens}.
 */
public final class TextChunker {

    private final TokenCounter tokenCounter;

    public TextChunker(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    public List<String> chunk(String text, int chunkTokens, int overlapTokens) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int budget = Math.max(16, chunkTokens);
        int overlap = Math.max(0, Math.min(overlapTokens, budget / 2));
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;
        for (String paragraph : paragraphs) {
            String piece = paragraph.strip();
            if (piece.isEmpty()) {
                continue;
            }
            int pieceTokens = Math.max(1, tokenCounter.count(piece));
            if (pieceTokens > budget) {
                flush(chunks, current);
                currentTokens = 0;
                chunks.addAll(tokenCounter.windows(piece, budget, overlap));
                continue;
            }
            if (currentTokens > 0 && currentTokens + pieceTokens > budget) {
                String finished = current.toString().strip();
                chunks.add(finished);
                String overlapText = overlapTail(finished, overlap);
                current.setLength(0);
                if (!overlapText.isEmpty()) {
                    current.append(overlapText).append("\n\n");
                    currentTokens = tokenCounter.count(current.toString());
                } else {
                    currentTokens = 0;
                }
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(piece);
            currentTokens += pieceTokens;
        }
        flush(chunks, current);
        return List.copyOf(chunks);
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            String finished = current.toString().strip();
            if (!finished.isEmpty()) {
                chunks.add(finished);
            }
            current.setLength(0);
        }
    }

    private String overlapTail(String text, int overlapTokens) {
        if (overlapTokens <= 0 || text.isBlank()) {
            return "";
        }
        List<String> windows = tokenCounter.windows(text, overlapTokens, 0);
        if (windows.isEmpty()) {
            return "";
        }
        return windows.get(windows.size() - 1);
    }
}
