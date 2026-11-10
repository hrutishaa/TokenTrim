package io.tokentrim.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tokentrim.api.dto.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads OpenAI tool definitions and assistant tool calls. */
public final class ToolCalls {

    private ToolCalls() {
    }

    public record Call(String id, String name, String arguments) {
    }

    public static List<Call> callsOf(ChatMessage message) {
        JsonNode calls = message.getToolCalls();
        if (calls == null || !calls.isArray()) {
            return List.of();
        }
        List<Call> parsed = new ArrayList<>();
        for (JsonNode call : calls) {
            String id = text(call, "id");
            JsonNode function = call.path("function");
            String name = text(function, "name");
            if (name.isBlank()) {
                name = text(call, "name");
            }
            String arguments = argumentsText(function.path("arguments"));
            if (!name.isBlank()) {
                parsed.add(new Call(id, name, arguments));
            }
        }
        return parsed;
    }

    public static Map<String, String> namesByCallId(List<ChatMessage> messages) {
        Map<String, String> names = new LinkedHashMap<>();
        if (messages == null) {
            return names;
        }
        for (ChatMessage message : messages) {
            for (Call call : callsOf(message)) {
                if (call.id() != null && !call.id().isBlank()) {
                    names.put(call.id(), call.name());
                }
            }
        }
        return names;
    }

    public static String toolName(JsonNode tool) {
        if (tool == null) {
            return "";
        }
        String name = text(tool.path("function"), "name");
        if (name.isBlank()) {
            name = text(tool, "name");
        }
        return name;
    }

    public static String description(JsonNode tool) {
        if (tool == null) {
            return "";
        }
        String description = text(tool.path("function"), "description");
        if (description.isBlank()) {
            description = text(tool, "description");
        }
        return description;
    }

    public static boolean required(JsonNode tool) {
        if (tool == null) {
            return false;
        }
        if (tool.path("required").asBoolean(false)) {
            return true;
        }
        return tool.path("function").path("required").asBoolean(false);
    }

    public static String forcedName(JsonNode toolChoice) {
        if (toolChoice == null || !toolChoice.isObject()) {
            return "";
        }
        String name = text(toolChoice.path("function"), "name");
        if (name.isBlank()) {
            name = text(toolChoice, "name");
        }
        return name;
    }

    public static String signature(String name, String arguments, ObjectMapper mapper) {
        return (name == null ? "" : name) + "\n" + normalizeArguments(arguments, mapper);
    }

    public static String normalizeArguments(String arguments, ObjectMapper mapper) {
        if (arguments == null || arguments.isBlank()) {
            return "";
        }
        String trimmed = arguments.strip();
        try {
            JsonNode node = mapper.readTree(trimmed);
            return mapper.writeValueAsString(sort(node, mapper));
        } catch (Exception ignored) {
            return trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        }
    }

    private static JsonNode sort(JsonNode node, ObjectMapper mapper) {
        if (node.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                sorted.set(name, sort(node.get(name), mapper));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = mapper.createArrayNode();
            node.forEach(child -> array.add(sort(child, mapper)));
            return array;
        }
        return node;
    }

    private static String argumentsText(JsonNode arguments) {
        if (arguments == null || arguments.isMissingNode() || arguments.isNull()) {
            return "";
        }
        if (arguments.isTextual()) {
            return arguments.asText("");
        }
        return arguments.toString();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
