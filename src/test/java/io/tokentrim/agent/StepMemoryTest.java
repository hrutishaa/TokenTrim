package io.tokentrim.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.token.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StepMemoryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TokenCounter tokens = new TokenCounter("cl100k_base");

    @Test
    void foldsOldStepsAndLeavesTheTaskAndLatestUserVerbatim() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getAgent().getStepMemory().setRecentSteps(1);
        properties.getAgent().getStepMemory().setTokenBudget(80);
        StepMemory memory = new StepMemory(tokens, properties.getAgent().getStepMemory());

        String task = "Fix the billing export job. The task id is TASK-8841.";
        String latest = "What is the next concrete step for TASK-8841?";
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("You are a careful coding agent."));
        messages.add(ChatMessage.user(task));
        messages.add(call("call_1", "read_file"));
        messages.add(tool("call_1", "invoice row ".repeat(80)));
        messages.add(call("call_2", "search_docs"));
        messages.add(tool("call_2", "found the exporter"));
        messages.add(ChatMessage.user(latest));

        List<ChatMessage> folded = memory.fold(messages);
        assertThat(folded.stream().filter(message -> "user".equals(message.getRole())).map(ChatMessage::text))
                .containsExactly(task, latest);
        assertThat(folded.stream().anyMatch(message -> message.text().startsWith("Progress so far:"))).isTrue();
        assertThat(folded.stream().anyMatch(message -> "call_2".equals(message.getToolCallId()))).isTrue();
        assertThat(folded.stream().noneMatch(message -> "call_1".equals(message.getToolCallId()))).isTrue();
        assertThat(folded.get(folded.size() - 1).text()).isEqualTo(latest);
    }

    private ChatMessage call(String id, String name) {
        ChatMessage message = ChatMessage.assistant("");
        ObjectNode call = mapper.createObjectNode();
        call.put("id", id);
        call.put("type", "function");
        call.putObject("function").put("name", name).put("arguments", "{\"path\":\"Export.java\"}");
        message.setToolCalls(mapper.createArrayNode().add(call));
        return message;
    }

    private static ChatMessage tool(String id, String content) {
        ChatMessage message = ChatMessage.of("tool", content);
        message.setToolCallId(id);
        return message;
    }
}
