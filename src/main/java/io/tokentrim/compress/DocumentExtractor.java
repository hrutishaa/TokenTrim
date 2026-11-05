package io.tokentrim.compress;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls {@code <document>} and {@code <context>} blocks out of a message so RAG can
 * score them as chunks instead of forwarding the whole paste.
 */
public final class DocumentExtractor {

    private static final Pattern TAG = Pattern.compile("(?is)<(document|context)\\b[^>]*>(.*?)</\\1>");

    public Extraction extract(String text) {
        if (text == null || text.isEmpty()) {
            return new Extraction("", List.of());
        }
        Matcher matcher = TAG.matcher(text);
        StringBuilder remaining = new StringBuilder();
        List<String> documents = new ArrayList<>();
        while (matcher.find()) {
            String body = matcher.group(2) == null ? "" : matcher.group(2).strip();
            if (!body.isEmpty()) {
                documents.add(body);
            }
            matcher.appendReplacement(remaining, " ");
        }
        matcher.appendTail(remaining);
        return new Extraction(remaining.toString(), List.copyOf(documents));
    }

    public record Extraction(String remaining, List<String> documents) {
    }
}
