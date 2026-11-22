package io.tokentrim.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.HashingEmbeddingModel;
import io.tokentrim.token.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSchemaPrunerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TokenTrimProperties properties = TokenTrimProperties.createDefault();
    private final ToolSchemaPruner pruner = new ToolSchemaPruner(
            new HashingEmbeddingModel(256),
            new TokenCounter("cl100k_base"),
            mapper,
            properties.getAgent().getSchemaPruning());

    @Test
    void keepsRequiredAndAlreadyUsedToolsAndTrimsDescriptions() {
        properties.getAgent().getSchemaPruning().setTopK(3);
        properties.getAgent().getSchemaPruning().setMaxDescriptionTokens(24);
        ArrayNode tools = mapper.createArrayNode();
        tools.add(tool("read_file", "Read a source file from the billing export job.", false));
        tools.add(tool("audit_log", "Unrelated archive of cafeteria menus and sports scores. ".repeat(12), true));
        tools.add(tool("sourdough", "Bake sourdough bread with flour water and salt. ".repeat(12), false));
        tools.add(tool("weather", "Forecast the weather in cities around the world. ".repeat(12), false));
        tools.add(tool("search_docs", "Search documentation for the billing export failure.", false));

        ChatMessage assistant = ChatMessage.assistant("");
        ObjectNode call = mapper.createObjectNode();
        call.put("id", "call_1");
        call.put("type", "function");
        call.putObject("function").put("name", "read_file").put("arguments", "{\"path\":\"Export.java\"}");
        assistant.setToolCalls(mapper.createArrayNode().add(call));

        ArrayNode pruned = (ArrayNode) pruner.prune(
                tools,
                java.util.List.of(ChatMessage.user("Fix the billing export job."), assistant),
                null);

        Set<String> names = names(pruned);
        assertThat(names).contains("read_file", "audit_log");
        assertThat(names).doesNotContain("sourdough");
        String audit = description(pruned, "audit_log");
        assertThat(audit.length()).isLessThan(tool("audit_log", "Unrelated archive of cafeteria menus and sports scores. ".repeat(12), true)
                .path("function").path("description").asText().length());
        assertThat(audit).endsWith("…");
    }

    private ObjectNode tool(String name, String description, boolean required) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        tool.put("required", required);
        ObjectNode function = tool.putObject("function");
        function.put("name", name);
        function.put("description", description);
        function.putObject("parameters").put("type", "object");
        return tool;
    }

    private static Set<String> names(ArrayNode tools) {
        Set<String> names = new HashSet<>();
        tools.forEach(tool -> names.add(tool.path("function").path("name").asText()));
        return names;
    }

    private static String description(ArrayNode tools, String name) {
        for (var tool : tools) {
            if (name.equals(tool.path("function").path("name").asText())) {
                return tool.path("function").path("description").asText();
            }
        }
        throw new AssertionError(name);
    }
}
