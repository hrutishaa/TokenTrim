package io.tokentrim;

import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatCompletionResponse;
import io.tokentrim.upstream.UpstreamChatClient;

import java.util.concurrent.atomic.AtomicInteger;

public final class CountingUpstream implements UpstreamChatClient {

    private final UpstreamChatClient delegate;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile ChatCompletionRequest lastRequest;

    public CountingUpstream(UpstreamChatClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatCompletionResponse complete(ChatCompletionRequest request) {
        calls.incrementAndGet();
        lastRequest = request;
        return delegate.complete(request);
    }

    public int calls() {
        return calls.get();
    }

    public ChatCompletionRequest lastRequest() {
        return lastRequest;
    }
}
