package io.tokentrim.api;

public final class TokenTrimHeaders {

    public static final String CACHE = "X-TokenTrim-Cache";
    public static final String MODEL = "X-TokenTrim-Model";
    public static final String REQUESTED_MODEL = "X-TokenTrim-Requested-Model";
    public static final String ROUTE = "X-TokenTrim-Route";
    public static final String COMPLEXITY = "X-TokenTrim-Complexity";
    public static final String TOKENS_ORIGINAL = "X-TokenTrim-Tokens-Original";
    public static final String TOKENS_SENT = "X-TokenTrim-Tokens-Sent";
    public static final String TOKENS_SAVED = "X-TokenTrim-Tokens-Saved";
    public static final String COST_SAVED = "X-TokenTrim-Cost-Saved-Usd";
    public static final String LATENCY = "X-TokenTrim-Latency-Ms";
    public static final String QUALITY = "X-TokenTrim-Quality";
    public static final String CACHE_SIMILARITY = "X-TokenTrim-Cache-Similarity";
    public static final String RAG_CHUNKS = "X-TokenTrim-Rag-Chunks";
    public static final String RUN_ID = "X-TokenTrim-Run-Id";
    public static final String RUN_TOKENS = "X-TokenTrim-Run-Tokens";
    public static final String BUDGET_WARNING = "X-TokenTrim-Budget-Warning";
    public static final String LOOP_DETECTED = "X-TokenTrim-Loop-Detected";
    public static final String SAVED_TOOL_COMPACTION = "X-TokenTrim-Saved-Tool-Compaction";
    public static final String SAVED_SCHEMA_PRUNING = "X-TokenTrim-Saved-Schema-Pruning";
    public static final String SAVED_LOOP_DEDUPE = "X-TokenTrim-Saved-Loop-Dedupe";
    public static final String SAVED_STEP_MEMORY = "X-TokenTrim-Saved-Step-Memory";

    private TokenTrimHeaders() {
    }
}
