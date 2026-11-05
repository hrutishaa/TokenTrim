package io.tokentrim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All knobs for the proxy. Field initializers are the defaults; {@code application.yml}
 * and environment variables override them.
 */
@ConfigurationProperties(prefix = "tokentrim")
public class TokenTrimProperties {

    private final Upstream upstream = new Upstream();
    private final Security security = new Security();
    private final Tokenizer tokenizer = new Tokenizer();
    private final Embeddings embeddings = new Embeddings();
    private final Compression compression = new Compression();
    private final Cache cache = new Cache();
    private final Routing routing = new Routing();
    private final Rag rag = new Rag();
    private final Quality quality = new Quality();
    private final Pricing pricing = new Pricing();

    public static TokenTrimProperties createDefault() {
        return new TokenTrimProperties();
    }

    public Upstream getUpstream() {
        return upstream;
    }

    public Security getSecurity() {
        return security;
    }

    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    public Embeddings getEmbeddings() {
        return embeddings;
    }

    public Compression getCompression() {
        return compression;
    }

    public Cache getCache() {
        return cache;
    }

    public Routing getRouting() {
        return routing;
    }

    public Rag getRag() {
        return rag;
    }

    public Quality getQuality() {
        return quality;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public static class Upstream {
        private String mode = "http";
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(90);

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Security {
        private String proxyApiKey = "";

        public String getProxyApiKey() {
            return proxyApiKey;
        }

        public void setProxyApiKey(String proxyApiKey) {
            this.proxyApiKey = proxyApiKey;
        }
    }

    public static class Tokenizer {
        private String encoding = "cl100k_base";

        public String getEncoding() {
            return encoding;
        }

        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }
    }

    public static class Embeddings {
        private String provider = "hashing";
        private String model = "text-embedding-3-small";
        private int dimensions = 256;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }
    }

    public static class Compression {
        private boolean enabled = true;
        private int historyTokenBudget = 768;
        private int maxSummaryTokens = 160;
        private List<String> boilerplatePhrases = defaultPhrases();

        private static List<String> defaultPhrases() {
            return new ArrayList<>(List.of(
                    "as an ai language model,",
                    "as a language model,",
                    "as an artificial intelligence,",
                    "sure, i'd be happy to help",
                    "sure, i would be happy to help",
                    "i'd be happy to help",
                    "certainly!",
                    "of course!",
                    "i hope this helps",
                    "i hope that helps",
                    "let me know if you need anything else",
                    "let me know if you have any other questions",
                    "feel free to ask",
                    "please note that",
                    "it is important to note that",
                    "it's important to note that"
            ));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getHistoryTokenBudget() {
            return historyTokenBudget;
        }

        public void setHistoryTokenBudget(int historyTokenBudget) {
            this.historyTokenBudget = historyTokenBudget;
        }

        public int getMaxSummaryTokens() {
            return maxSummaryTokens;
        }

        public void setMaxSummaryTokens(int maxSummaryTokens) {
            this.maxSummaryTokens = maxSummaryTokens;
        }

        public List<String> getBoilerplatePhrases() {
            return boilerplatePhrases;
        }

        public void setBoilerplatePhrases(List<String> boilerplatePhrases) {
            this.boilerplatePhrases = boilerplatePhrases;
        }
    }

    public static class Cache {
        private boolean enabled = true;
        private String backend = "memory";
        private double similarityThreshold = 0.72;
        private Duration ttl = Duration.ofHours(24);
        private int maxEntries = 5000;
        private final Redis redis = new Redis();
        private final PgVector pgvector = new PgVector();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        public Redis getRedis() {
            return redis;
        }

        public PgVector getPgvector() {
            return pgvector;
        }
    }

    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private String password = "";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class PgVector {
        private String url = "jdbc:postgresql://localhost:5432/tokentrim";
        private String username = "tokentrim";
        private String password = "tokentrim";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Routing {
        private boolean enabled = true;
        private boolean honorRequestedModel = false;
        private String fallbackModel = "gpt-4o-mini";
        private List<RouteRule> routes = new ArrayList<>(List.of(
                new RouteRule("simple", 0.40, "gpt-4o-mini"),
                new RouteRule("complex", 1.00, "gpt-4o")
        ));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isHonorRequestedModel() {
            return honorRequestedModel;
        }

        public void setHonorRequestedModel(boolean honorRequestedModel) {
            this.honorRequestedModel = honorRequestedModel;
        }

        public String getFallbackModel() {
            return fallbackModel;
        }

        public void setFallbackModel(String fallbackModel) {
            this.fallbackModel = fallbackModel;
        }

        public List<RouteRule> getRoutes() {
            return routes;
        }

        public void setRoutes(List<RouteRule> routes) {
            this.routes = routes;
        }
    }

    public static class RouteRule {
        private String name;
        private double maxComplexity;
        private String model;

        public RouteRule() {
        }

        public RouteRule(String name, double maxComplexity, String model) {
            this.name = name;
            this.maxComplexity = maxComplexity;
            this.model = model;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getMaxComplexity() {
            return maxComplexity;
        }

        public void setMaxComplexity(double maxComplexity) {
            this.maxComplexity = maxComplexity;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Rag {
        private boolean enabled = true;
        private int topK = 4;
        private int chunkTokens = 180;
        private int overlapTokens = 30;
        private int tokenBudget = 640;
        private double minScore = 0.20;
        private boolean keepBestChunk = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getChunkTokens() {
            return chunkTokens;
        }

        public void setChunkTokens(int chunkTokens) {
            this.chunkTokens = chunkTokens;
        }

        public int getOverlapTokens() {
            return overlapTokens;
        }

        public void setOverlapTokens(int overlapTokens) {
            this.overlapTokens = overlapTokens;
        }

        public int getTokenBudget() {
            return tokenBudget;
        }

        public void setTokenBudget(int tokenBudget) {
            this.tokenBudget = tokenBudget;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public boolean isKeepBestChunk() {
            return keepBestChunk;
        }

        public void setKeepBestChunk(boolean keepBestChunk) {
            this.keepBestChunk = keepBestChunk;
        }
    }

    public static class Quality {
        private boolean enabled = false;
        private double sampleRate = 0.0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
        }
    }

    public static class Pricing {
        private Map<String, ModelPrice> models = defaultPrices();

        private static Map<String, ModelPrice> defaultPrices() {
            Map<String, ModelPrice> prices = new LinkedHashMap<>();
            prices.put("gpt-4o", new ModelPrice(2.50, 10.00));
            prices.put("gpt-4o-mini", new ModelPrice(0.15, 0.60));
            prices.put("default", new ModelPrice(0.50, 1.50));
            return prices;
        }

        public Map<String, ModelPrice> getModels() {
            return models;
        }

        public void setModels(Map<String, ModelPrice> models) {
            this.models = models;
        }
    }

    public static class ModelPrice {
        /** USD per 1,000,000 input tokens. Estimates, not a bill. */
        private double inputPerMillion;
        /** USD per 1,000,000 output tokens. */
        private double outputPerMillion;

        public ModelPrice() {
        }

        public ModelPrice(double inputPerMillion, double outputPerMillion) {
            this.inputPerMillion = inputPerMillion;
            this.outputPerMillion = outputPerMillion;
        }

        public double getInputPerMillion() {
            return inputPerMillion;
        }

        public void setInputPerMillion(double inputPerMillion) {
            this.inputPerMillion = inputPerMillion;
        }

        public double getOutputPerMillion() {
            return outputPerMillion;
        }

        public void setOutputPerMillion(double outputPerMillion) {
            this.outputPerMillion = outputPerMillion;
        }
    }
}
