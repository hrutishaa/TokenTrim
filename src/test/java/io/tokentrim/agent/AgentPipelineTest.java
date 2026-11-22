package io.tokentrim.agent;

import io.tokentrim.TestRig;
import io.tokentrim.api.TokenTrimHeaders;
import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentPipelineTest {

    @Test
    void softLimitAddsAWarningHeader() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getAgent().getRunBudget().setSoftLimitTokens(5);
        properties.getAgent().getRunBudget().setHardLimitTokens(100_000);
        TestRig rig = new TestRig(properties);
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(ChatMessage.user("What is the capital of France?")));

        var result = rig.pipeline.execute(request, "soft-run");

        assertThat(result.headers().getFirst(TokenTrimHeaders.BUDGET_WARNING)).contains("soft limit");
        assertThat(result.headers().getFirst(TokenTrimHeaders.RUN_ID)).isEqualTo("soft-run");
        assertThat(rig.upstream.calls()).isEqualTo(1);
    }

    @Test
    void hardLimitStopsBeforeTheUpstreamCall() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getAgent().getRunBudget().setHardLimitTokens(8);
        TestRig rig = new TestRig(properties);
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4o");
        request.setMessages(List.of(ChatMessage.user(
                "Explain the billing export job in detail. ".repeat(30))));

        assertThatThrownBy(() -> rig.pipeline.execute(request, "hard-run"))
                .isInstanceOf(RunBudgetExceededException.class);
        assertThat(rig.upstream.calls()).isZero();
    }
}
