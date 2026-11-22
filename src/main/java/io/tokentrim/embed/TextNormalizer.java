package io.tokentrim.embed;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonical form used both as the semantic-cache exact key and as the hashing embedder input.
 * Contractions, punctuation, and stopwords are folded so "What's the capital of France?"
 * and "What is the capital of France?" land on the same key.
 */
public final class TextNormalizer {

    private static final List<Replacement> CONTRACTIONS = List.of(
            new Replacement(Pattern.compile("\\bwhat's\\b"), "what is"),
            new Replacement(Pattern.compile("\\bwhats\\b"), "what is"),
            new Replacement(Pattern.compile("\\bit's\\b"), "it is"),
            new Replacement(Pattern.compile("\\bi'm\\b"), "i am"),
            new Replacement(Pattern.compile("\\bi'd\\b"), "i would"),
            new Replacement(Pattern.compile("\\bdon't\\b"), "do not"),
            new Replacement(Pattern.compile("\\bcan't\\b"), "cannot"),
            new Replacement(Pattern.compile("\\bwon't\\b"), "will not"),
            new Replacement(Pattern.compile("\\blet's\\b"), "let us"),
            new Replacement(Pattern.compile("\\bthat's\\b"), "that is"),
            new Replacement(Pattern.compile("\\bthere's\\b"), "there is"),
            new Replacement(Pattern.compile("\\bthey're\\b"), "they are"),
            new Replacement(Pattern.compile("\\byou're\\b"), "you are")
    );

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "of", "to", "and", "or", "for", "in", "on", "at", "by", "with", "from", "as",
            "it", "this", "that", "these", "those", "there", "here",
            "i", "you", "we", "they", "he", "she", "me", "my", "your", "our",
            "do", "does", "did", "doing", "can", "could", "would", "should", "will", "just",
            "what", "whats", "who", "whom", "whose", "which", "when", "where", "why", "how",
            "please", "kindly", "hey", "hello", "hi", "thanks", "thank",
            "tell", "explain", "describe", "give", "need", "want", "know",
            "about", "into", "onto", "over", "under", "than", "then", "also", "very",
            "am", "im", "let", "us", "lets"
    );

    private TextNormalizer() {
    }

    public static String canonical(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Replacement replacement : CONTRACTIONS) {
            lower = replacement.pattern.matcher(lower).replaceAll(replacement.replacement);
        }
        lower = lower.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
        if (lower.isEmpty()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (word.isEmpty() || STOP.contains(word)) {
                continue;
            }
            if (!kept.isEmpty()) {
                kept.append(' ');
            }
            kept.append(word);
        }
        return kept.isEmpty() ? lower : kept.toString();
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
