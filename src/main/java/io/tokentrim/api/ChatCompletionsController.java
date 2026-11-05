package io.tokentrim.api;

import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.pipeline.ChatPipeline;
import io.tokentrim.pipeline.PipelineResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChatCompletionsController {

    private final ChatPipeline pipeline;
    private final TokenTrimProperties properties;

    public ChatCompletionsController(ChatPipeline pipeline, TokenTrimProperties properties) {
        this.pipeline = pipeline;
        this.properties = properties;
    }

    @PostMapping(path = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> complete(@RequestBody ChatCompletionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return ResponseEntity.badRequest().body(Errors.of(
                    "messages is required and must contain at least one message",
                    "invalid_request_error"));
        }
        if (Boolean.TRUE.equals(request.getStream())) {
            return ResponseEntity.badRequest().body(Errors.of(
                    "stream=true is not supported. TokenTrim caches and measures full completions; omit stream or set it to false.",
                    "invalid_request_error"));
        }
        PipelineResult result = pipeline.execute(request);
        return ResponseEntity.ok().headers(result.headers()).body(result.response());
    }

    @GetMapping("/models")
    public Map<String, Object> models() {
        List<Map<String, String>> data = new ArrayList<>();
        for (TokenTrimProperties.RouteRule rule : properties.getRouting().getRoutes()) {
            if (rule.getModel() == null) {
                continue;
            }
            data.add(Map.of(
                    "id", rule.getModel(),
                    "object", "model",
                    "owned_by", "tokentrim"
            ));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("object", "list");
        body.put("data", data);
        return body;
    }
}
