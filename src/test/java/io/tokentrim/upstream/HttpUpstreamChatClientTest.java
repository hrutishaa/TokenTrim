package io.tokentrim.upstream;

import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatCompletionResponse;
import io.tokentrim.api.dto.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpUpstreamChatClientTest {

    @Test
    void postsTheCompletionAndParsesTheResponse() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"gpt-4o-mini",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"Paris"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpUpstreamChatClient client = client(server, "test-key");
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel("gpt-4o-mini");
            request.setMessages(List.of(ChatMessage.user("Capital of France?")));
            request.setUser("alice");

            ChatCompletionResponse response = client.complete(request);

            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(body.get()).contains("\"user\":\"alice\"");
            assertThat(body.get()).doesNotContain("tokentrim");
            assertThat(response.answerText()).isEqualTo("Paris");
            assertThat(response.getUsage().getTotalTokens()).isEqualTo(4);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void propagatesUpstreamErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = "{\"error\":{\"message\":\"slow down\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpUpstreamChatClient client = client(server, "test-key");
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel("gpt-4o-mini");
            request.setMessages(List.of(ChatMessage.user("Hi")));
            assertThatThrownBy(() -> client.complete(request))
                    .isInstanceOf(UpstreamException.class)
                    .satisfies(error -> {
                        UpstreamException upstream = (UpstreamException) error;
                        assertThat(upstream.status()).isEqualTo(429);
                        assertThat(upstream.body()).contains("slow down");
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingApiKeyFailsBeforeTheCall() {
        RestClient restClient = RestClient.builder().baseUrl("http://127.0.0.1:9/v1").build();
        HttpUpstreamChatClient client = new HttpUpstreamChatClient(restClient, "");
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(ChatMessage.user("Hi")));
        assertThatThrownBy(() -> client.complete(request))
                .isInstanceOf(UpstreamException.class)
                .satisfies(error -> assertThat(((UpstreamException) error).status()).isEqualTo(502));
    }

    private HttpUpstreamChatClient client(HttpServer server, String apiKey) {
        int port = server.getAddress().getPort();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("http://127.0.0.1:" + port + "/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new HttpUpstreamChatClient(restClient, apiKey);
    }
}
