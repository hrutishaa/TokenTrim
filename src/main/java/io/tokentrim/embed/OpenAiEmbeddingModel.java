package io.tokentrim.embed;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls {@code POST /embeddings} on the configured upstream. The returned vector is L2-normalized
 * so cosine similarity matches the hashing embedder's contract. {@code dimensions} must match
 * the column width when the pgvector backend is in use.
 */
public final class OpenAiEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final int dimensions;

    public OpenAiEmbeddingModel(RestClient restClient, String apiKey, String model, int dimensions) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null || model.isBlank() ? "text-embedding-3-small" : model;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI embeddings require TOKENTRIM_UPSTREAM_API_KEY");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text == null ? "" : text);
        if (dimensions > 0) {
            body.put("dimensions", dimensions);
        }
        JsonNode response = restClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("Embedding provider returned an empty body");
        }
        JsonNode vector = response.path("data").path(0).path("embedding");
        if (!vector.isArray() || vector.isEmpty()) {
            throw new IllegalStateException("Embedding provider returned no vector");
        }
        float[] out = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            out[i] = (float) vector.get(i).asDouble();
        }
        return VectorMath.normalize(out);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String name() {
        return model;
    }
}
