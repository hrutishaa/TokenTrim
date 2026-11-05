package io.tokentrim.pipeline;

import io.tokentrim.CountingUpstream;
import io.tokentrim.FixturePrompts;
import io.tokentrim.TestRig;
import io.tokentrim.api.TokenTrimHeaders;
import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.api.dto.TokenTrimOptions;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.MessageTexts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPipelineTest {

    @Test
    void missThenNearDuplicateHitDoesNotCallUpstreamAgain() {
        TestRig rig = new TestRig();
        ChatCompletionRequest first = question("What is the capital of France?");
        PipelineResult miss = rig.pipeline.execute(first);
        PipelineResult hit = rig.pipeline.execute(question("What's the capital of France?"));

        assertThat(miss.cacheHit()).isFalse();
        assertThat(hit.cacheHit()).isTrue();
        assertThat(hit.sentTokens()).isZero();
        assertThat(hit.headers().getFirst(TokenTrimHeaders.CACHE)).isEqualTo("HIT");
        assertThat(rig.upstream.calls()).isEqualTo(1);
        assertThat(hit.response().answerText()).isEqualTo(miss.response().answerText());
        assertThat(miss.routedModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void paraphraseOfTheSameQuestionHits() {
        TestRig rig = new TestRig();
        rig.pipeline.execute(question("What is the capital of France?"));
        PipelineResult hit = rig.pipeline.execute(question("Tell me the capital city of France"));
        assertThat(hit.cacheHit()).isTrue();
        assertThat(hit.headers().getFirst(TokenTrimHeaders.CACHE)).isEqualTo("HIT");
        assertThat(rig.upstream.calls()).isEqualTo(1);
    }

    @Test
    void differentQuestionIsAMiss() {
        TestRig rig = new TestRig();
        rig.pipeline.execute(question("What is the capital of France?"));
        PipelineResult germany = rig.pipeline.execute(question("What is the capital of Germany?"));
        assertThat(germany.cacheHit()).isFalse();
        assertThat(germany.response().answerText()).contains("Germany");
        assertThat(rig.upstream.calls()).isEqualTo(2);
    }

    @Test
    void hardPromptIsRoutedUp() {
        TestRig rig = new TestRig();
        PipelineResult result = rig.pipeline.execute(FixturePrompts.hard());
        assertThat(result.routeName()).isEqualTo("complex");
        assertThat(result.routedModel()).isEqualTo("gpt-4o");
        assertThat(rig.upstream.lastRequest().getModel()).isEqualTo("gpt-4o");
    }

    @Test
    void compressionAndRagShrinkTheOutboundPrompt() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getCompression().setHistoryTokenBudget(512);
        properties.getRag().setTopK(1);
        properties.getRag().setTokenBudget(260);
        TestRig rig = new TestRig(properties);

        PipelineResult result = rig.pipeline.execute(FixturePrompts.verboseEasy());
        String outbound = MessageTexts.join(rig.upstream.lastRequest().getMessages());

        assertThat(result.tokensSaved()).isGreaterThan(result.originalTokens() / 2);
        assertThat(result.sentTokens()).isLessThan(result.originalTokens());
        assertThat(outbound).contains("Paris");
        assertThat(outbound).doesNotContain("Sourdough");
        assertThat(outbound).contains("Earlier question number 0");
        assertThat(outbound).doesNotContain("Earlier question number 3");
        assertThat(outbound).doesNotContain("Earlier question number 4");
        assertThat(outbound.toLowerCase()).doesNotContain("i'd be happy to help");
        assertThat(outbound).contains("Earlier conversation, condensed");
        assertThat(outbound).doesNotContain("\n\n\n");
        assertThat(rig.upstream.lastRequest().getModel()).isEqualTo("gpt-4o-mini");
        assertThat(rig.upstream.lastRequest().getTokentrim()).isNull();
        assertThat(result.ragChunks()).isEqualTo(1);
    }

    @Test
    void qualityCheckComparesAgainstTheUncompressedPrompt() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getQuality().setEnabled(true);
        TestRig rig = new TestRig(properties);
        ChatCompletionRequest request = question("What is the capital of France?");
        TokenTrimOptions options = new TokenTrimOptions();
        options.setQualityCheck(true);
        request.setTokentrim(options);

        PipelineResult result = rig.pipeline.execute(request);

        assertThat(rig.upstream.calls()).isEqualTo(2);
        assertThat(result.headers().getFirst(TokenTrimHeaders.QUALITY)).isNotBlank();
        assertThat(Double.parseDouble(result.headers().getFirst(TokenTrimHeaders.QUALITY))).isBetween(0.0, 1.0);
    }

    @Test
    void passthroughFieldsSurviveTheCopy() {
        TestRig rig = new TestRig();
        ChatCompletionRequest request = rig.objectMapper.convertValue(
                java.util.Map.of(
                        "model", "gpt-4o",
                        "messages", java.util.List.of(java.util.Map.of("role", "user", "content", "Hello there")),
                        "user", "alice",
                        "metadata", java.util.Map.of("k", "v")
                ),
                ChatCompletionRequest.class);
        rig.pipeline.execute(request);
        assertThat(rig.upstream.lastRequest().getUser()).isEqualTo("alice");
        assertThat(rig.upstream.lastRequest().getAdditionalProperties()).containsKey("metadata");
    }

    private static ChatCompletionRequest question(String text) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4o");
        request.setMessages(List.of(
                ChatMessage.system("You are a concise assistant."),
                ChatMessage.user(text)));
        return request;
    }
}
