package io.tokentrim.api;

import java.util.Map;

public final class Errors {

    private Errors() {
    }

    public static Map<String, Object> of(String message, String type) {
        return Map.of("error", Map.of(
                "message", message,
                "type", type
        ));
    }

    public static String json(String message, String type) {
        return "{\"error\":{\"message\":\"" + escape(message) + "\",\"type\":\"" + type + "\"}}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
