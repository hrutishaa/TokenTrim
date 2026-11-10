package io.tokentrim.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import io.tokentrim.TestRig;
import io.tokentrim.api.TokenTrimHeaders;
import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.embed.MessageTexts;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproducible agent-mode savings check. Run with
 * {@code ./mvnw -Dtest=AgentTokenSavingsBenchmarkTest -Dsurefire.useFile=false test}.
 */
class AgentTokenSavingsBenchmarkTest {

    @Test
    void agentFixtureSavesTokensOnEveryTechnique() throws Exception {
        TestRig rig = new TestRig();
        ChatCompletionRequest request = rig.objectMapper.readValue(
                Files.readString(Path.of("samples/agent-run.json")),
                ChatCompletionRequest.class);
        String latest = "What is the next concrete step for TASK-8841?";
        String task = "Fix the billing export job. The task id is TASK-8841. Do not change this sentence.";

        PipelineResult result = rig.pipeline.execute(request, "agent-bench");
        int compaction = Integer.parseInt(result.headers().getFirst(TokenTrimHeaders.SAVED_TOOL_COMPACTION));
        int schema = Integer.parseInt(result.headers().getFirst(TokenTrimHeaders.SAVED_SCHEMA_PRUNING));
        int loops = Integer.parseInt(result.headers().getFirst(TokenTrimHeaders.SAVED_LOOP_DEDUPE));
        int steps = Integer.parseInt(result.headers().getFirst(TokenTrimHeaders.SAVED_STEP_MEMORY));
        double ratio = result.tokensSaved() / (double) result.originalTokens();
        String outbound = MessageTexts.join(rig.upstream.lastRequest().getMessages());
        String report = """
                TokenTrim agent benchmark
                original tokens : %d
                sent tokens     : %d
                tokens saved    : %d (%.1f%%)
                tool_compaction : %d
                schema_pruning  : %d
                loop_dedupe     : %d
                step_memory     : %d
                loop header     : %s
                tools sent      : %d
                """.formatted(
                result.originalTokens(),
                result.sentTokens(),
                result.tokensSaved(),
                ratio * 100,
                compaction,
                schema,
                loops,
                steps,
                result.headers().getFirst(TokenTrimHeaders.LOOP_DETECTED),
                rig.upstream.lastRequest().getTools().size());
        System.out.println(report);

        assertThat(ratio).as("agent savings%n%s", report).isGreaterThanOrEqualTo(0.45);
        assertThat(compaction).isGreaterThan(0);
        assertThat(schema).isGreaterThan(0);
        assertThat(loops).isGreaterThan(0);
        assertThat(steps).isGreaterThan(0);
        assertThat(result.headers().getFirst(TokenTrimHeaders.LOOP_DETECTED)).isNotBlank();
        assertThat(lastUser(rig.upstream.lastRequest())).isEqualTo(latest);
        assertThat(outbound).contains(task);
        JsonNode tools = rig.upstream.lastRequest().getTools();
        assertThat(names(tools)).contains("read_file", "read_logs", "search_docs", "audit_log");
        assertThat(tools.size()).isEqualTo(8);
        assertThat(tools.size()).isLessThan(request.getTools().size());
        assertThat(rig.runs.find("agent-bench")).isPresent();
    }

    private static String lastUser(ChatCompletionRequest request) {
        String last = "";
        for (ChatMessage message : request.getMessages()) {
            if ("user".equals(message.getRole())) {
                last = message.text();
            }
        }
        return last;
    }

    private static java.util.Set<String> names(JsonNode tools) {
        java.util.Set<String> names = new java.util.HashSet<>();
        tools.forEach(tool -> names.add(tool.path("function").path("name").asText()));
        return names;
    }
}
