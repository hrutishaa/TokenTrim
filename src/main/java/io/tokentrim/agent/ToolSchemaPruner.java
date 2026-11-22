package io.tokentrim.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.EmbeddingModel;
import io.tokentrim.embed.MessageTexts;
import io.tokentrim.embed.VectorMath;
import io.tokentrim.token.TokenCounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the tools that match the current step, plus any tool already called or marked required.
 * Long descriptions are trimmed on the tools that remain.
 */
public final class ToolSchemaPruner {

    private final EmbeddingModel embeddings;
    private final TokenCounter tokenCounter;
    private final ObjectMapper mapper;
    private final TokenTrimProperties.SchemaPruning settings;

    public ToolSchemaPruner(
            EmbeddingModel embeddings,
            TokenCounter tokenCounter,
            ObjectMapper mapper,
            TokenTrimProperties.SchemaPruning settings
    ) {
        this.embeddings = embeddings;
        this.tokenCounter = tokenCounter;
        this.mapper = mapper;
        this.settings = settings;
    }

    public JsonNode prune(JsonNode tools, List<ChatMessage> messages, JsonNode toolChoice) {
        return prune(tools, messages, toolChoice, messages);
    }

    public JsonNode prune(
            JsonNode tools,
            List<ChatMessage> messages,
            JsonNode toolChoice,
            List<ChatMessage> historyForPins
    ) {
        if (!settings.isEnabled() || tools == null || !tools.isArray() || tools.isEmpty()) {
            return tools;
        }
        Set<String> pinned = pinnedNames(historyForPins, toolChoice);
        String query = MessageTexts.lastUser(messages);
        String assistant = lastAssistantText(messages);
        if (!assistant.isBlank()) {
            query = query + "\n" + assistant;
        }
        float[] queryVector = embeddings.embed(query);

        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            JsonNode tool = tools.get(i);
            String name = ToolCalls.toolName(tool);
            boolean keep = ToolCalls.required(tool) || pinned.contains(name);
            double score = VectorMath.cosine(queryVector, embeddings.embed(name + " " + ToolCalls.description(tool)));
            ranked.add(new Ranked(i, tool, name, score, keep));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed());

        int topK = Math.max(1, settings.getTopK());
        List<Ranked> selected = new ArrayList<>();
        for (Ranked tool : ranked) {
            if (tool.pinned()) {
                selected.add(tool);
            }
        }
        for (Ranked tool : ranked) {
            if (selected.size() >= topK) {
                break;
            }
            if (!selected.contains(tool)) {
                selected.add(tool);
            }
        }
        selected.sort(Comparator.comparingInt(Ranked::index));

        ArrayNode out = mapper.createArrayNode();
        for (Ranked tool : selected) {
            out.add(trimDescription(tool.tool()));
        }
        return out;
    }

    private JsonNode trimDescription(JsonNode tool) {
        if (!tool.isObject()) {
            return tool;
        }
        int maxTokens = Math.max(16, settings.getMaxDescriptionTokens());
        ObjectNode copy = tool.deepCopy();
        JsonNode function = copy.get("function");
        if (function != null && function.isObject()) {
            String description = ToolCalls.description(copy);
            String trimmed = trim(description, maxTokens);
            if (!trimmed.equals(description)) {
                ((ObjectNode) function).put("description", trimmed);
            }
        } else {
            String description = ToolCalls.description(copy);
            String trimmed = trim(description, maxTokens);
            if (!trimmed.equals(description)) {
                copy.put("description", trimmed);
            }
        }
        return copy;
    }

    private String trim(String description, int maxTokens) {
        if (description == null || description.isBlank() || tokenCounter.count(description) <= maxTokens) {
            return description == null ? "" : description;
        }
        List<String> windows = tokenCounter.windows(description, maxTokens, 0);
        String head = windows.isEmpty() ? "" : windows.get(0).strip();
        return head + "…";
    }

    private static Set<String> pinnedNames(List<ChatMessage> messages, JsonNode toolChoice) {
        Set<String> names = new HashSet<>();
        if (messages != null) {
            for (ChatMessage message : messages) {
                for (ToolCalls.Call call : ToolCalls.callsOf(message)) {
                    names.add(call.name());
                }
            }
        }
        String forced = ToolCalls.forcedName(toolChoice);
        if (!forced.isBlank()) {
            names.add(forced);
        }
        return names;
    }

    private static String lastAssistantText(List<ChatMessage> messages) {
        String last = "";
        if (messages == null) {
            return last;
        }
        for (ChatMessage message : messages) {
            if ("assistant".equals(message.getRole()) && !message.text().isBlank()) {
                last = message.text();
            }
        }
        return last;
    }

    private record Ranked(int index, JsonNode tool, String name, double score, boolean pinned) {
    }
}
