package io.tokentrim.upstream;

import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatCompletionResponse;
import io.tokentrim.embed.MessageTexts;
import io.tokentrim.token.TokenCounter;

/**
 * Local stand-in for an LLM. It echoes the latest user turn so the proxy, cache, and
 * savings accounting can be exercised without an API key.
 */
public final class EchoUpstreamChatClient implements UpstreamChatClient {

    private final TokenCounter tokenCounter;

    public EchoUpstreamChatClient(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    @Override
    public ChatCompletionResponse complete(ChatCompletionRequest request) {
        String model = request.getModel() == null ? "echo" : request.getModel();
        String question = MessageTexts.lastUser(request.getMessages());
        String clipped = question.length() > 240 ? question.substring(0, 240) + "..." : question;
        String answer = "Echo: " + clipped;
        int promptTokens = tokenCounter.countMessages(request.getMessages());
        int completionTokens = tokenCounter.count(answer);
        return ChatCompletionResponse.of(model, answer, promptTokens, completionTokens);
    }
}
