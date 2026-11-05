package io.tokentrim.cache;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Nearest-neighbor cache on Postgres + pgvector. Cosine distance ({@code <=>}) ranks
 * candidates; the similarity threshold is applied in Java. An HNSW index is created
 * when the server supports it.
 */
public final class PgVectorSemanticCache implements SemanticCache {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSemanticCache.class);

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final int dimensions;
    private final Duration ttl;
    private final Clock clock;

    public PgVectorSemanticCache(String url, String username, String password, int dimensions, Duration ttl, Clock clock) {
        this.dimensions = Vectors.safeDimensions(dimensions);
        this.ttl = ttl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(username);
        pool.setPassword(password);
        pool.setDriverClassName("org.postgresql.Driver");
        pool.setMaximumPoolSize(4);
        pool.setPoolName("tokentrim-pgvector");
        this.dataSource = pool;
        this.jdbc = new JdbcTemplate(pool);
        initSchema();
    }

    DataSource dataSource() {
        return dataSource;
    }

    private void initSchema() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS tokentrim_cache (
                    id varchar(64) PRIMARY KEY,
                    model varchar(200) NOT NULL,
                    prompt_key text NOT NULL,
                    prompt_preview text,
                    embedding vector(%d) NOT NULL,
                    response_json text NOT NULL,
                    prompt_tokens integer NOT NULL,
                    completion_tokens integer NOT NULL,
                    created_at_ms bigint NOT NULL
                )
                """.formatted(dimensions));
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS tokentrim_cache_prompt_idx
                ON tokentrim_cache (model, prompt_key)
                """);
        try {
            jdbc.execute("""
                    CREATE INDEX IF NOT EXISTS tokentrim_cache_embedding_hnsw
                    ON tokentrim_cache USING hnsw (embedding vector_cosine_ops)
                    """);
        } catch (RuntimeException exception) {
            log.warn("HNSW index was not created; nearest-neighbor queries still run with an exact scan: {}",
                    exception.getMessage());
        }
        log.info("Semantic cache using pgvector ({} dimensions)", dimensions);
    }

    @Override
    public Optional<CacheMatch> find(String promptKey, float[] query, String model, double threshold) {
        long cutoff = cutoff();
        List<CacheMatch> exact = jdbc.query("""
                        SELECT id, model, prompt_key, prompt_preview, response_json,
                               prompt_tokens, completion_tokens, created_at_ms, 1.0 AS similarity
                        FROM tokentrim_cache
                        WHERE model = ? AND prompt_key = ? AND created_at_ms >= ?
                        LIMIT 1
                        """,
                (rs, row) -> new CacheMatch(map(rs), rs.getDouble("similarity")),
                model, promptKey, cutoff);
        if (!exact.isEmpty()) {
            return Optional.of(exact.get(0));
        }
        if (query == null || query.length != dimensions) {
            return Optional.empty();
        }
        String literal = Vectors.toLiteral(query);
        String prefix = CacheKeys.systemPrefix(promptKey);
        List<CacheMatch> neighbors = prefix.isEmpty()
                ? jdbc.query("""
                        SELECT id, model, prompt_key, prompt_preview, response_json,
                               prompt_tokens, completion_tokens, created_at_ms,
                               1 - (embedding <=> CAST(? AS vector)) AS similarity
                        FROM tokentrim_cache
                        WHERE model = ? AND created_at_ms >= ?
                        ORDER BY embedding <=> CAST(? AS vector)
                        LIMIT 5
                        """,
                (rs, row) -> new CacheMatch(map(rs), rs.getDouble("similarity")),
                literal, model, cutoff, literal)
                : jdbc.query("""
                        SELECT id, model, prompt_key, prompt_preview, response_json,
                               prompt_tokens, completion_tokens, created_at_ms,
                               1 - (embedding <=> CAST(? AS vector)) AS similarity
                        FROM tokentrim_cache
                        WHERE model = ? AND created_at_ms >= ? AND prompt_key LIKE ? ESCAPE '\\'
                        ORDER BY embedding <=> CAST(? AS vector)
                        LIMIT 5
                        """,
                (rs, row) -> new CacheMatch(map(rs), rs.getDouble("similarity")),
                literal, model, cutoff, likePrefix(prefix), literal);
        CacheMatch best = null;
        for (CacheMatch match : neighbors) {
            if (match.similarity() >= threshold && (best == null || match.similarity() > best.similarity())) {
                best = match;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public void store(CacheEntry entry) {
        if (entry.getEmbedding() == null || entry.getEmbedding().length != dimensions) {
            throw new IllegalArgumentException("Embedding length does not match pgvector column width " + dimensions);
        }
        long cutoff = cutoff();
        jdbc.update("DELETE FROM tokentrim_cache WHERE created_at_ms < ?", cutoff);
        jdbc.update("DELETE FROM tokentrim_cache WHERE model = ? AND prompt_key = ?", entry.getModel(), entry.getPromptKey());
        jdbc.update("""
                        INSERT INTO tokentrim_cache
                            (id, model, prompt_key, prompt_preview, embedding, response_json,
                             prompt_tokens, completion_tokens, created_at_ms)
                        VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?)
                        """,
                entry.getId(),
                entry.getModel(),
                entry.getPromptKey(),
                entry.getPromptPreview(),
                Vectors.toLiteral(entry.getEmbedding()),
                entry.getResponseJson(),
                entry.getPromptTokens(),
                entry.getCompletionTokens(),
                entry.getCreatedAtEpochMs());
    }

    @Override
    public int size() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM tokentrim_cache WHERE created_at_ms >= ?",
                Integer.class,
                cutoff());
        return count == null ? 0 : count;
    }

    @Override
    public void clear() {
        jdbc.update("DELETE FROM tokentrim_cache");
    }

    public void close() {
        dataSource.close();
    }

    private static String likePrefix(String prefix) {
        return prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private long cutoff() {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0L;
        }
        return clock.instant().minus(ttl).toEpochMilli();
    }

    private static CacheEntry map(ResultSet rs) throws SQLException {
        return new CacheEntry(
                rs.getString("id"),
                rs.getString("model"),
                rs.getString("prompt_key"),
                rs.getString("prompt_preview"),
                new float[0],
                rs.getString("response_json"),
                rs.getInt("prompt_tokens"),
                rs.getInt("completion_tokens"),
                rs.getLong("created_at_ms")
        );
    }
}
