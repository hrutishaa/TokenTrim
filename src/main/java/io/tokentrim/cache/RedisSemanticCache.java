package io.tokentrim.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Persists cache entries in vanilla Redis. Exact keys are O(1). Near-duplicate search
 * loads the capped entry list and scores it in-process, which is fine for a few thousand
 * entries. Use the pgvector backend when the cache outgrows a linear scan.
 */
public final class RedisSemanticCache implements SemanticCache, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisSemanticCache.class);
    private static final String IDS = "tokentrim:cache:ids";

    private final StringRedisTemplate template;
    private final LettuceConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final int maxEntries;
    private final Duration ttl;
    private final Clock clock;

    public RedisSemanticCache(
            String host,
            int port,
            String password,
            ObjectMapper objectMapper,
            int maxEntries,
            Duration ttl,
            Clock clock
    ) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            configuration.setPassword(password);
        }
        this.connectionFactory = new LettuceConnectionFactory(configuration);
        this.connectionFactory.afterPropertiesSet();
        this.template = new StringRedisTemplate(connectionFactory);
        this.template.afterPropertiesSet();
        this.objectMapper = objectMapper;
        this.maxEntries = Math.max(1, maxEntries);
        this.ttl = ttl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        ping();
        log.info("Semantic cache connected to Redis at {}:{}", host, port);
    }

    private void ping() {
        RedisConnection connection = connectionFactory.getConnection();
        try {
            connection.ping();
        } finally {
            connection.close();
        }
    }

    @Override
    public Optional<CacheMatch> find(String promptKey, float[] query, String model, double threshold) {
        String exactId = template.opsForValue().get(exactKey(model, promptKey));
        if (exactId != null) {
            CacheEntry exact = read(exactId);
            if (exact != null && !SemanticMatcher.expired(exact, clock.instant(), ttl)) {
                return Optional.of(new CacheMatch(exact, 1.0));
            }
        }
        List<String> ids = template.opsForList().range(IDS, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }
        List<CacheEntry> loaded = new ArrayList<>();
        for (String id : ids) {
            CacheEntry entry = read(id);
            if (entry == null) {
                template.opsForList().remove(IDS, 1, id);
                continue;
            }
            if (SemanticMatcher.expired(entry, clock.instant(), ttl)) {
                deleteEntry(entry);
                continue;
            }
            loaded.add(entry);
        }
        return SemanticMatcher.best(sameSystem(loaded, promptKey), query, model, threshold, clock.instant(), ttl);
    }

    @Override
    public void store(CacheEntry entry) {
        String previous = template.opsForValue().get(exactKey(entry.getModel(), entry.getPromptKey()));
        if (previous != null && !previous.equals(entry.getId())) {
            template.delete(entryKey(previous));
            template.opsForList().remove(IDS, 1, previous);
        }
        try {
            String json = objectMapper.writeValueAsString(entry);
            Duration entryTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofDays(3650) : ttl;
            template.opsForValue().set(entryKey(entry.getId()), json, entryTtl);
            template.opsForValue().set(exactKey(entry.getModel(), entry.getPromptKey()), entry.getId(), entryTtl);
            template.opsForList().leftPush(IDS, entry.getId());
            Long size = template.opsForList().size(IDS);
            while (size != null && size > maxEntries) {
                String evicted = template.opsForList().rightPop(IDS);
                if (evicted == null) {
                    break;
                }
                template.delete(entryKey(evicted));
                size--;
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize cache entry", exception);
        }
    }

    @Override
    public int size() {
        Long size = template.opsForList().size(IDS);
        return size == null ? 0 : Math.toIntExact(size);
    }

    @Override
    public void clear() {
        List<String> ids = template.opsForList().range(IDS, 0, -1);
        if (ids != null) {
            for (String id : ids) {
                template.delete(entryKey(id));
            }
        }
        template.delete(IDS);
    }

    @Override
    public void close() {
        connectionFactory.destroy();
    }

    private static List<CacheEntry> sameSystem(List<CacheEntry> loaded, String promptKey) {
        String prefix = CacheKeys.systemPrefix(promptKey);
        if (prefix.isEmpty()) {
            return loaded;
        }
        List<CacheEntry> matched = new ArrayList<>();
        for (CacheEntry entry : loaded) {
            if (entry.getPromptKey() != null && entry.getPromptKey().startsWith(prefix)) {
                matched.add(entry);
            }
        }
        return matched;
    }

    private CacheEntry read(String id) {
        String raw = template.opsForValue().get(entryKey(id));
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, CacheEntry.class);
        } catch (JsonProcessingException exception) {
            log.warn("Dropping unreadable cache entry {}", id);
            return null;
        }
    }

    private void deleteEntry(CacheEntry entry) {
        template.delete(entryKey(entry.getId()));
        template.delete(exactKey(entry.getModel(), entry.getPromptKey()));
        template.opsForList().remove(IDS, 1, entry.getId());
    }

    private static String entryKey(String id) {
        return "tokentrim:cache:entry:" + id;
    }

    private static String exactKey(String model, String promptKey) {
        return "tokentrim:cache:exact:" + sha256(model + "\n" + promptKey);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
