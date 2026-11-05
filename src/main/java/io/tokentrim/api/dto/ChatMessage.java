package io.tokentrim.api.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
)
public final class ChatMessage {

    private String role;
    private JsonNode content;
    private String name;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public static ChatMessage of(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.role = role;
        message.content = content == null ? null : TextNode.valueOf(content);
        return message;
    }

    public static ChatMessage system(String content) {
        return of("system", content);
    }

    public static ChatMessage user(String content) {
        return of("user", content);
    }

    public static ChatMessage assistant(String content) {
        return of("assistant", content);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public JsonNode getContent() {
        return content;
    }

    public void setContent(JsonNode content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonAnySetter
    public void putAdditional(String key, Object value) {
        additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    /** True when content is a string (or absent), so compression can rewrite it safely. */
    @JsonIgnore
    public boolean isPlainText() {
        return content == null || content.isNull() || content.isTextual();
    }

    @JsonIgnore
    public String text() {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : content) {
                JsonNode text = part.get("text");
                if (text != null && text.isTextual()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text.asText());
                }
            }
            return builder.toString();
        }
        return "";
    }

    public ChatMessage withText(String text) {
        if (!isPlainText()) {
            return this;
        }
        ChatMessage copy = copyShell();
        copy.content = text == null ? NullNode.getInstance() : TextNode.valueOf(text);
        return copy;
    }

    private ChatMessage copyShell() {
        ChatMessage copy = new ChatMessage();
        copy.role = role;
        copy.name = name;
        copy.content = content;
        copy.additionalProperties = new LinkedHashMap<>(additionalProperties);
        return copy;
    }
}
