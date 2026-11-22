package io.tokentrim.rag;

import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.EmbeddingModel;
import io.tokentrim.embed.VectorMath;
import io.tokentrim.token.TokenCounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Scores every chunk against the user question and keeps the top ones that fit in the
 * RAG token budget. Callers paste whole documents; only the selected excerpts are forwarded.
 */
public final class RagTrimmer {

    private final TokenTrimProperties.Rag settings;
    private final TokenCounter tokenCounter;
    private final EmbeddingModel embeddings;
    private final TextChunker chunker;

    public RagTrimmer(
            TokenTrimProperties properties,
            TokenCounter tokenCounter,
            EmbeddingModel embeddings,
            TextChunker chunker
    ) {
        this.settings = properties.getRag();
        this.tokenCounter = tokenCounter;
        this.embeddings = embeddings;
        this.chunker = chunker;
    }

    public Selection select(List<String> documents, String query, Integer topKOverride) {
        if (documents == null || documents.isEmpty()) {
            return Selection.empty();
        }
        int topK = Math.max(1, topKOverride == null ? settings.getTopK() : topKOverride);
        List<String> chunks = new ArrayList<>();
        for (String document : documents) {
            if (document == null || document.isBlank()) {
                continue;
            }
            chunks.addAll(chunker.chunk(document, settings.getChunkTokens(), settings.getOverlapTokens()));
        }
        if (chunks.isEmpty()) {
            return Selection.empty();
        }
        float[] queryVector = embeddings.embed(query == null ? "" : query);
        List<ScoredChunk> scored = new ArrayList<>();
        for (String chunk : chunks) {
            double score = VectorMath.cosine(queryVector, embeddings.embed(chunk));
            scored.add(new ScoredChunk(chunk, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        List<ScoredChunk> selected = new ArrayList<>();
        int used = 0;
        for (ScoredChunk chunk : scored) {
            if (selected.size() >= topK) {
                break;
            }
            if (chunk.score() < settings.getMinScore() && !selected.isEmpty()) {
                continue;
            }
            if (chunk.score() < settings.getMinScore() && selected.isEmpty() && !settings.isKeepBestChunk()) {
                continue;
            }
            int tokens = Math.max(1, tokenCounter.count(chunk.text()));
            if (!selected.isEmpty() && used + tokens > settings.getTokenBudget()) {
                continue;
            }
            if (selected.isEmpty() && tokens > settings.getTokenBudget()) {
                List<String> shrunk = tokenCounter.windows(chunk.text(), settings.getTokenBudget(), 0);
                if (!shrunk.isEmpty()) {
                    selected.add(new ScoredChunk(shrunk.get(0), chunk.score()));
                    used += tokenCounter.count(shrunk.get(0));
                }
                break;
            }
            selected.add(chunk);
            used += tokens;
        }
        if (selected.isEmpty() && settings.isKeepBestChunk()) {
            ScoredChunk best = scored.get(0);
            List<String> shrunk = tokenCounter.windows(best.text(), Math.max(16, settings.getTokenBudget()), 0);
            if (!shrunk.isEmpty()) {
                selected.add(new ScoredChunk(shrunk.get(0), best.score()));
            }
        }
        return new Selection(List.copyOf(selected), render(selected));
    }

    private static String render(List<ScoredChunk> selected) {
        if (selected.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("Relevant excerpts:\n");
        int index = 1;
        for (ScoredChunk chunk : selected) {
            builder.append('[').append(index++).append("] (relevance ")
                    .append(String.format(Locale.US, "%.2f", chunk.score()))
                    .append(")\n")
                    .append(chunk.text().strip())
                    .append("\n\n");
        }
        return builder.toString().strip();
    }

    public record ScoredChunk(String text, double score) {
    }

    public record Selection(List<ScoredChunk> chunks, String rendered) {
        public static Selection empty() {
            return new Selection(List.of(), "");
        }
    }
}
