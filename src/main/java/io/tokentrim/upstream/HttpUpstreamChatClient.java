package io.tokentrim.upstream;

import io.tokentrim.api.Errors;
import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatCompletionResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

public final class HttpUpstreamChatClient implements UpstreamChatClient {

    static final String USER_AGENT = "tokentrim/0.1.0";

    private final RestClient restClient;
    private final String apiKey;

    public HttpUpstreamChatClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public ChatCompletionResponse complete(ChatCompletionRequest request) {
        if (apiKey.isBlank()) {
            throw new UpstreamException(502, Errors.json(
                    "Upstream API key is not configured. Set TOKENTRIM_UPSTREAM_API_KEY, or TOKENTRIM_UPSTREAM_MODE=echo for a local demo.",
                    "upstream_error"));
        }
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, httpResponse) -> {
                        String raw = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new UpstreamException(httpResponse.getStatusCode().value(), raw);
                    })
                    .body(ChatCompletionResponse.class);
            if (response == null) {
                throw new UpstreamException(502, Errors.json("Upstream returned an empty body", "upstream_error"));
            }
            return response;
        } catch (UpstreamException exception) {
            throw exception;
        } catch (RestClientException exception) {
            UpstreamException unwrapped = unwrap(exception);
            if (unwrapped != null) {
                throw unwrapped;
            }
            throw new UpstreamException(502, Errors.json(
                    "Upstream request failed: " + exception.getMessage(),
                    "upstream_error"));
        }
    }

    private static UpstreamException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UpstreamException upstream) {
                return upstream;
            }
            current = current.getCause();
        }
        return null;
    }
}
