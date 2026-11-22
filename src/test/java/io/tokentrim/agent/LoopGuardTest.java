package io.tokentrim.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoopGuardTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void collapsesOlderDuplicateCallsAndFlagsTheThreshold() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getAgent().getLoopGuard().setRepeatThreshold(3);
        LoopGuard guard = new LoopGuard(mapper, properties.getAgent().getLoopGuard());

        List<ChatMessage> messages = List.of(
                ChatMessage.user("Fix the billing export job."),
                call("call_1"),
                tool("call_1", "alpha ".repeat(40)),
                call("call_2"),
                tool("call_2", "alpha ".repeat(40)),
                call("call_3"),
                tool("call_3", "alpha ".repeat(40)));

        LoopGuard.Result result = guard.apply(messages);
        assertThat(result.detected()).isTrue();
        assertThat(result.count()).isEqualTo(3);
        assertThat(text(result.messages(), "call_1")).contains("[duplicate]");
        assertThat(text(result.messages(), "call_2")).contains("[duplicate]");
        assertThat(text(result.messages(), "call_3")).contains("alpha");
        assertThat(result.messages().get(0).text()).isEqualTo("Fix the billing export job.");
    }

    @Test
    void treatsReorderedJsonArgumentsAsTheSameCall() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getAgent().getLoopGuard().setRepeatThreshold(2);
        LoopGuard guard = new LoopGuard(mapper, properties.getAgent().getLoopGuard());
        List<ChatMessage> messages = List.of(
                callWithArgs("call_a", "{\"path\":\"A.java\",\"limit\":5}"),
                tool("call_a", "first"),
                callWithArgs("call_b", "{\n  \"limit\": 5,\n  \"path\": \"A.java\"\n}"),
                tool("call_b", "second"));
        LoopGuard.Result result = guard.apply(messages);
        assertThat(result.detected()).isTrue();
        assertThat(text(result.messages(), "call_a")).contains("[duplicate]");
        assertThat(text(result.messages(), "call_b")).isEqualTo("second");
    }

    private ChatMessage call(String id) {
        return callWithArgs(id, "{\"query\":\"billing export\"}");
    }

    private ChatMessage callWithArgs(String id, String arguments) {
        ChatMessage message = ChatMessage.assistant("");
        ObjectNode call = mapper.createObjectNode();
        call.put("id", id);
        call.put("type", "function");
        call.putObject("function").put("name", "search_docs").put("arguments", arguments);
        message.setToolCalls(mapper.createArrayNode().add(call));
        return message;
    }

    private static ChatMessage tool(String id, String content) {
        ChatMessage message = ChatMessage.of("tool", content);
        message.setToolCallId(id);
        return message;
    }

    private static String text(List<ChatMessage> messages, String id) {
        return messages.stream().filter(message -> id.equals(message.getToolCallId())).findFirst().orElseThrow().text();
    }
}
