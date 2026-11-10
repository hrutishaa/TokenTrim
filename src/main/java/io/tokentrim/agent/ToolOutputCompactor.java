package io.tokentrim.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.token.TokenCounter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shrinks older {@code role: tool} payloads. The latest tool result is left unchanged
 * unless it exceeds the hard cap.
 */
public final class ToolOutputCompactor {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern LOG_HINT = Pattern.compile("(?i)\\b(error|fatal|exception|warn)\\b|\\d{4}-\\d{2}-\\d{2}");

    private final TokenCounter tokenCounter;
    private final ObjectMapper mapper;
    private final TokenTrimProperties.ToolCompaction settings;

    public ToolOutputCompactor(TokenCounter tokenCounter, ObjectMapper mapper, TokenTrimProperties.ToolCompaction settings) {
        this.tokenCounter = tokenCounter;
        this.mapper = mapper;
        this.settings = settings;
    }

    public List<ChatMessage> compact(List<ChatMessage> messages) {
        if (!settings.isEnabled() || messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        int latest = -1;
        for (int i = 0; i < messages.size(); i++) {
            if ("tool".equals(messages.get(i).getRole())) {
                latest = i;
            }
        }
        if (latest < 0) {
            return messages;
        }
        Map<String, String> names = ToolCalls.namesByCallId(messages);
        List<ChatMessage> out = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (!"tool".equals(message.getRole()) || !message.isPlainText()) {
                out.add(message);
                continue;
            }
            String original = message.text();
            int tokens = tokenCounter.count(original);
            if (i == latest && tokens <= settings.getHardCapTokens()) {
                out.add(message);
                continue;
            }
            String toolName = names.getOrDefault(message.getToolCallId(), message.getName());
            int budget = i == latest ? settings.getHardCapTokens() : settings.budgetFor(toolName);
            out.add(message.withText(shrink(original, budget)));
        }
        return out;
    }

    String shrink(String text, int budget) {
        String current = text == null ? "" : text;
        if (looksLikeHtml(current)) {
            current = stripHtml(current);
        }
        String trimmed = current.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            current = minifyJson(trimmed);
        } else if (looksLikeLog(current)) {
            current = compactLog(current);
        } else {
            current = dedupeLines(current);
        }
        return fit(current, Math.max(32, budget));
    }

    private String minifyJson(String text) {
        try {
            JsonNode node = mapper.readTree(text);
            JsonNode compacted = compactNode(node);
            return mapper.writeValueAsString(compacted);
        } catch (Exception ignored) {
            return dedupeLines(text);
        }
    }

    private JsonNode compactNode(JsonNode node) {
        if (node.isArray()) {
            int total = node.size();
            int keep = Math.max(1, settings.getMaxArrayItems());
            ArrayNode array = mapper.createArrayNode();
            int limit = Math.min(total, keep);
            for (int i = 0; i < limit; i++) {
                array.add(compactNode(node.get(i)));
            }
            if (total > keep) {
                array.add("… " + (total - keep) + " more items");
            }
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> object.set(entry.getKey(), compactNode(entry.getValue())));
            return object;
        }
        return node;
    }

    private String compactLog(String text) {
        List<String> lines = lines(text);
        if (lines.isEmpty()) {
            return text;
        }
        int head = Math.max(1, settings.getLogHeadLines());
        int tail = Math.max(1, settings.getLogTailLines());
        LinkedHashSet<Integer> keep = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(head, lines.size()); i++) {
            keep.add(i);
        }
        for (int i = 0; i < lines.size(); i++) {
            if (LOG_HINT.matcher(lines.get(i)).find() && lines.get(i).toLowerCase(Locale.ROOT).matches(".*(error|fatal|exception).*")) {
                keep.add(i);
            }
        }
        for (int i = Math.max(0, lines.size() - tail); i < lines.size(); i++) {
            keep.add(i);
        }
        List<String> selected = new ArrayList<>();
        int omitted = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (keep.contains(i)) {
                if (omitted > 0) {
                    selected.add("… " + omitted + " lines omitted");
                    omitted = 0;
                }
                selected.add(lines.get(i));
            } else {
                omitted++;
            }
        }
        return dedupeLines(String.join("\n", selected));
    }

    private static String dedupeLines(String text) {
        List<String> lines = lines(text);
        if (lines.size() < 2) {
            return text == null ? "" : text.strip();
        }
        StringBuilder out = new StringBuilder();
        String previous = null;
        int run = 0;
        for (String line : lines) {
            if (line.equals(previous)) {
                run++;
                continue;
            }
            flush(out, previous, run);
            previous = line;
            run = 1;
        }
        flush(out, previous, run);
        return out.toString().strip();
    }

    private static void flush(StringBuilder out, String line, int run) {
        if (line == null || run <= 0) {
            return;
        }
        if (!out.isEmpty()) {
            out.append('\n');
        }
        out.append(line);
        if (run > 1) {
            out.append("  (repeated ").append(run).append(" times)");
        }
    }

    private String fit(String text, int budget) {
        if (tokenCounter.count(text) <= budget) {
            return text;
        }
        List<String> windows = tokenCounter.windows(text, Math.max(16, budget - 12), 0);
        String head = windows.isEmpty() ? "" : windows.get(0);
        int omitted = Math.max(0, tokenCounter.count(text) - tokenCounter.count(head));
        return head.strip() + "\n… truncated, about " + omitted + " tokens omitted";
    }

    private static boolean looksLikeHtml(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("<html") || lower.contains("<div") || lower.contains("<p>") || lower.contains("</");
    }

    private static boolean looksLikeLog(String text) {
        int newlines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                newlines++;
            }
        }
        return newlines >= 6 && LOG_HINT.matcher(text).find();
    }

    private static String stripHtml(String text) {
        String stripped = TAG.matcher(text).replaceAll(" ");
        stripped = stripped.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        return stripped.replaceAll("[ \\t]{2,}", " ").replaceAll("\\n{3,}", "\n\n").strip();
    }

    private static List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String[] split = text.replace("\r\n", "\n").split("\n", -1);
        List<String> lines = new ArrayList<>(split.length);
        for (String line : split) {
            if (!line.isBlank() || lines.isEmpty()) {
                lines.add(line.stripTrailing());
            }
        }
        return lines;
    }
}
