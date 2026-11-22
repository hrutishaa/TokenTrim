package io.tokentrim.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Optional extension object on a chat-completions request. Unknown to OpenAI,
 * stripped before the upstream call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenTrimOptions {

    private List<String> documents;

    @JsonProperty("skip_cache")
    private Boolean skipCache;

    @JsonProperty("skip_compression")
    private Boolean skipCompression;

    @JsonProperty("quality_check")
    private Boolean qualityCheck;

    @JsonProperty("force_model")
    private String forceModel;

    @JsonProperty("history_token_budget")
    private Integer historyTokenBudget;

    @JsonProperty("rag_top_k")
    private Integer ragTopK;

    public List<String> getDocuments() {
        return documents;
    }

    public void setDocuments(List<String> documents) {
        this.documents = documents;
    }

    public boolean isSkipCache() {
        return Boolean.TRUE.equals(skipCache);
    }

    public void setSkipCache(Boolean skipCache) {
        this.skipCache = skipCache;
    }

    public boolean isSkipCompression() {
        return Boolean.TRUE.equals(skipCompression);
    }

    public void setSkipCompression(Boolean skipCompression) {
        this.skipCompression = skipCompression;
    }

    public boolean isQualityCheck() {
        return Boolean.TRUE.equals(qualityCheck);
    }

    public void setQualityCheck(Boolean qualityCheck) {
        this.qualityCheck = qualityCheck;
    }

    public String getForceModel() {
        return forceModel;
    }

    public void setForceModel(String forceModel) {
        this.forceModel = forceModel;
    }

    public Integer getHistoryTokenBudget() {
        return historyTokenBudget;
    }

    public void setHistoryTokenBudget(Integer historyTokenBudget) {
        this.historyTokenBudget = historyTokenBudget;
    }

    public Integer getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(Integer ragTopK) {
        this.ragTopK = ragTopK;
    }
}
