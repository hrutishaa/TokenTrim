package io.tokentrim.route;

import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.token.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRouterTest {

    private final TokenCounter tokenCounter = new TokenCounter("cl100k_base");

    @Test
    void easyPromptUsesTheSmallModel() {
        RoutingDecision decision = router(false).route(
                List.of(ChatMessage.user("What is the capital of France?")),
                "gpt-4o",
                null);
        assertThat(decision.route()).isEqualTo("simple");
        assertThat(decision.model()).isEqualTo("gpt-4o-mini");
        assertThat(decision.complexity()).isLessThan(0.40);
    }

    @Test
    void hardPromptUsesTheLargeModelEvenIfTheClientAskedForMini() {
        RoutingDecision decision = router(false).route(
                List.of(ChatMessage.user(
                        "Prove step by step the time complexity of merge sort and implement the algorithm in Python.")),
                "gpt-4o-mini",
                null);
        assertThat(decision.route()).isEqualTo("complex");
        assertThat(decision.model()).isEqualTo("gpt-4o");
        assertThat(decision.complexity()).isGreaterThan(0.40);
    }

    @Test
    void honorRequestedModelSkipsTheDowngrade() {
        RoutingDecision decision = router(true).route(
                List.of(ChatMessage.user("What is the capital of France?")),
                "gpt-4o",
                null);
        assertThat(decision.model()).isEqualTo("gpt-4o");
        assertThat(decision.route()).isEqualTo("requested");
    }

    @Test
    void forceModelWins() {
        RoutingDecision decision = router(false).route(
                List.of(ChatMessage.user("What is the capital of France?")),
                "gpt-4o",
                "gpt-4.1");
        assertThat(decision.model()).isEqualTo("gpt-4.1");
        assertThat(decision.route()).isEqualTo("forced");
    }

    private ModelRouter router(boolean honorRequested) {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getRouting().setHonorRequestedModel(honorRequested);
        return new ModelRouter(properties, new ComplexityScorer(tokenCounter));
    }
}
