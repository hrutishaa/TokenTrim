package io.tokentrim.api.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
)
public class ChatCompletionResponse {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public static ChatCompletionResponse of(String model, String content, int promptTokens, int completionTokens) {
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        response.object = "chat.completion";
        response.created = Instant.now().getEpochSecond();
        response.model = model;
        Choice choice = new Choice();
        choice.index = 0;
        choice.message = ChatMessage.assistant(content);
        choice.finishReason = "stop";
        response.choices = List.of(choice);
        Usage usage = new Usage();
        usage.promptTokens = promptTokens;
        usage.completionTokens = completionTokens;
        usage.totalTokens = promptTokens + completionTokens;
        response.usage = usage;
        return response;
    }

    public String getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public Usage getUsage() {
        return usage;
    }

    @JsonIgnore
    public String answerText() {
        if (choices == null || choices.isEmpty() || choices.get(0).getMessage() == null) {
            return "";
        }
        return choices.get(0).getMessage().text();
    }

    @JsonAnySetter
    public void putAdditional(String key, Object value) {
        additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            setterVisibility = JsonAutoDetect.Visibility.NONE
    )
    public static class Choice {
        private Integer index;
        private ChatMessage message;

        @JsonProperty("finish_reason")
        private String finishReason;

        public Integer getIndex() {
            return index;
        }

        public ChatMessage getMessage() {
            return message;
        }

        public String getFinishReason() {
            return finishReason;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            setterVisibility = JsonAutoDetect.Visibility.NONE
    )
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }
    }
}
