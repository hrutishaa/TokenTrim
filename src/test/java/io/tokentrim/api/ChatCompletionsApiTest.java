package io.tokentrim.api;

import io.tokentrim.FixturePrompts;
import io.tokentrim.cache.SemanticCache;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.metrics.TokenTrimMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "tokentrim.upstream.mode=echo")
@AutoConfigureObservability(tracing = false)
@AutoConfigureMockMvc
class ChatCompletionsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SemanticCache cache;

    @Autowired
    private TokenTrimMetrics metrics;

    @Autowired
    private TokenTrimProperties properties;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        cache.clear();
        metrics.reset();
    }

    @Test
    void echoCompletionThenCacheHitAndStats() throws Exception {
        String body = objectMapper.writeValueAsString(FixturePrompts.verboseEasy());

        MvcResult first = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.choices[0].message.content").exists())
                .andReturn();

        assertThat(first.getResponse().getHeader(TokenTrimHeaders.CACHE)).isEqualTo("MISS");
        assertThat(first.getResponse().getHeader(TokenTrimHeaders.MODEL)).isEqualTo("gpt-4o-mini");
        int saved = Integer.parseInt(first.getResponse().getHeader(TokenTrimHeaders.TOKENS_SAVED));
        int original = Integer.parseInt(first.getResponse().getHeader(TokenTrimHeaders.TOKENS_ORIGINAL));
        assertThat(saved).isGreaterThan(original / 2);

        MvcResult second = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(second.getResponse().getHeader(TokenTrimHeaders.CACHE)).isEqualTo("HIT");
        assertThat(second.getResponse().getHeader(TokenTrimHeaders.TOKENS_SENT)).isEqualTo("0");

        mockMvc.perform(get("/v1/tokentrim/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests").value(2))
                .andExpect(jsonPath("$.cache_hits").value(1))
                .andExpect(jsonPath("$.cache_misses").value(1))
                .andExpect(jsonPath("$.cache_hit_rate").value(0.5));

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("gpt-4o-mini"));

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("tokentrim_tokens_saved_total")
                        .contains("tokentrim_cache_lookups_total"));

        assertThat(properties.getRouting().getRoutes())
                .extracting(TokenTrimProperties.RouteRule::getModel)
                .containsExactly("gpt-4o-mini", "gpt-4o");
    }

    @Test
    void rejectsEmptyMessagesAndStreaming() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\",\"messages\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("stream=true")));
    }
}
