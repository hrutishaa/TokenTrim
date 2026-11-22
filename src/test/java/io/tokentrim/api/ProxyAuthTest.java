package io.tokentrim.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "tokentrim.upstream.mode=echo",
        "tokentrim.security.proxy-api-key=secret"
})
@AutoConfigureMockMvc
class ProxyAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresTheProxyKeyOnV1AndLeavesHealthOpen() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").exists());

        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
