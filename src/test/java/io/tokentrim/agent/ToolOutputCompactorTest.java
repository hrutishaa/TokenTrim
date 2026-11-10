package io.tokentrim.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.token.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolOutputCompactorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TokenCounter tokens = new TokenCounter("cl100k_base");
    private final TokenTrimProperties.ToolCompaction settings =
            TokenTrimProperties.createDefault().getAgent().getToolCompaction();
    private final ToolOutputCompactor compactor = new ToolOutputCompactor(tokens, mapper, settings);

    @Test
    void minifiesJsonTruncatesArraysAndDedupesWhileKeepingTheLatestResult() {
        settings.setMaxArrayItems(3);
        settings.setDefaultTokenBudget(2000);
        String pretty = prettyArray(40);
        String latest = "{\"status\":\"ok\",\"note\":\"leave this pretty\"}";
        ChatMessage first = tool("call_old", "read_file", pretty);
        ChatMessage second = tool("call_new", "read_file", latest);

        List<ChatMessage> compacted = compactor.compact(List.of(
                ChatMessage.user("Inspect the export"),
                assistantCall("call_old", "read_file", "{}"),
                first,
                assistantCall("call_new", "read_file", "{}"),
                second));

        String oldText = text(compacted, "call_old");
        String newText = text(compacted, "call_new");
        assertThat(newText).isEqualTo(latest);
        assertThat(oldText).doesNotContain("\n  ");
        assertThat(oldText).contains("37 more items");
        assertThat(oldText).contains("INV-0");
        assertThat(oldText).doesNotContain("INV-30");
        assertThat(tokens.count(oldText)).isLessThan(tokens.count(pretty));
        assertThat(compacted.get(0).text()).isEqualTo("Inspect the export");
    }

    @Test
    void keepsLogHeadTailAndErrorLinesAndCollapsesRepeats() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            log.append("INFO heartbeat\n");
        }
        log.append("ERROR billing export failed on invoice 42\n");
        for (int i = 0; i < 8; i++) {
            log.append("INFO done ").append(i).append('\n');
        }
        String compacted = compactor.shrink(log.toString(), 120);
        assertThat(compacted).contains("ERROR billing export failed");
        assertThat(compacted).contains("repeated");
        assertThat(compacted).contains("INFO done 7");
        assertThat(tokens.count(compacted)).isLessThan(tokens.count(log.toString()));
    }

    @Test
    void stripsHtmlToText() {
        String html = "<html><body><h1>Export</h1><p>Invoice 42 is stuck.</p></body></html>";
        String compacted = compactor.shrink(html.repeat(8), 80);
        assertThat(compacted).contains("Invoice 42 is stuck");
        assertThat(compacted).doesNotContain("<p>");
    }

    private String prettyArray(int count) throws RuntimeException {
        try {
            ArrayNode array = mapper.createArrayNode();
            for (int i = 0; i < count; i++) {
                ObjectNode row = mapper.createObjectNode();
                row.put("invoice", "INV-" + i);
                row.put("status", "stuck");
                row.put("note", "billing export row " + i + " needs a human");
                array.add(row);
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(array);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ChatMessage tool(String id, String name, String content) {
        ChatMessage message = ChatMessage.of("tool", content);
        message.setToolCallId(id);
        message.setName(name);
        return message;
    }

    private ChatMessage assistantCall(String id, String name, String arguments) {
        ChatMessage message = ChatMessage.assistant("");
        message.setToolCalls(mapper.createArrayNode().add(call(id, name, arguments)));
        return message;
    }

    private ObjectNode call(String id, String name, String arguments) {
        ObjectNode call = mapper.createObjectNode();
        call.put("id", id);
        call.put("type", "function");
        ObjectNode function = call.putObject("function");
        function.put("name", name);
        function.put("arguments", arguments);
        return call;
    }

    private static String text(List<ChatMessage> messages, String callId) {
        return messages.stream()
                .filter(message -> callId.equals(message.getToolCallId()))
                .map(ChatMessage::text)
                .findFirst()
                .orElseThrow();
    }
}
