package io.tokentrim.upstream;

import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatCompletionResponse;

public interface UpstreamChatClient {

    ChatCompletionResponse complete(ChatCompletionRequest request);
}
