package io.tokentrim.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
)
public final class CacheEntry {

    private String id;
    private String model;
    private String promptKey;
    private String promptPreview;
    private float[] embedding;
    private String responseJson;
    private int promptTokens;
    private int completionTokens;
    private long createdAtEpochMs;

    public CacheEntry() {
    }

    public CacheEntry(
            String id,
            String model,
            String promptKey,
            String promptPreview,
            float[] embedding,
            String responseJson,
            int promptTokens,
            int completionTokens,
            long createdAtEpochMs
    ) {
        this.id = id;
        this.model = model;
        this.promptKey = promptKey;
        this.promptPreview = promptPreview;
        this.embedding = embedding;
        this.responseJson = responseJson;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public String getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public String getPromptKey() {
        return promptKey;
    }

    public String getPromptPreview() {
        return promptPreview;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public Instant createdAt() {
        return Instant.ofEpochMilli(createdAtEpochMs);
    }
}
