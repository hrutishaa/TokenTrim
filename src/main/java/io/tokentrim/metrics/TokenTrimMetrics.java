package io.tokentrim.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.tokentrim.api.dto.StatsSnapshot;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public final class TokenTrimMetrics {

    private final MeterRegistry registry;
    private final Counter requests;
    private final Counter originalTokens;
    private final Counter sentTokens;
    private final Counter savedTokens;
    private final Counter costSaved;
    private final Counter failures;
    private final Timer requestLatency;
    private final Timer upstreamLatency;
    private final DistributionSummary quality;

    private final LongAdder requestCount = new LongAdder();
    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();
    private final LongAdder bypassCount = new LongAdder();
    private final LongAdder originalSum = new LongAdder();
    private final LongAdder sentSum = new LongAdder();
    private final LongAdder savedSum = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder latencyNanos = new LongAdder();
    private final DoubleAdder costSum = new DoubleAdder();

    public TokenTrimMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.requests = Counter.builder("tokentrim.requests").description("Chat completion requests").register(registry);
        this.originalTokens = Counter.builder("tokentrim.tokens.original")
                .description("Tokens in the unmodified prompt")
                .register(registry);
        this.sentTokens = Counter.builder("tokentrim.tokens.sent")
                .description("Tokens actually sent upstream (zero on a cache hit)")
                .register(registry);
        this.savedTokens = Counter.builder("tokentrim.tokens.saved")
                .description("original minus sent, floored at zero")
                .register(registry);
        this.costSaved = Counter.builder("tokentrim.cost.saved.usd")
                .description("Estimated USD saved versus the unmodified prompt on the requested model")
                .baseUnit("usd")
                .register(registry);
        this.failures = Counter.builder("tokentrim.upstream.errors").register(registry);
        this.requestLatency = Timer.builder("tokentrim.request.latency")
                .description("End-to-end proxy latency")
                .register(registry);
        this.upstreamLatency = Timer.builder("tokentrim.upstream.latency")
                .description("Upstream model latency")
                .register(registry);
        this.quality = DistributionSummary.builder("tokentrim.quality.similarity")
                .description("Cosine similarity between the optimized answer and an uncompressed baseline")
                .baseUnit("ratio")
                .register(registry);
    }

    public void recordSuccess(
            boolean cacheEnabled,
            boolean cacheHit,
            int original,
            int sent,
            int saved,
            double costSavedUsd,
            long latencyNanos,
            String route,
            String model
    ) {
        requests.increment();
        originalTokens.increment(original);
        sentTokens.increment(sent);
        savedTokens.increment(saved);
        this.costSaved.increment(costSavedUsd);
        requestLatency.record(latencyNanos, TimeUnit.NANOSECONDS);
        Counter.builder("tokentrim.routing.decisions")
                .tag("route", route == null ? "unknown" : route)
                .tag("model", model == null ? "unknown" : model)
                .register(registry)
                .increment();
        if (cacheEnabled) {
            Counter.builder("tokentrim.cache.lookups")
                    .tag("result", cacheHit ? "hit" : "miss")
                    .register(registry)
                    .increment();
            if (cacheHit) {
                hitCount.increment();
            } else {
                missCount.increment();
            }
        } else {
            Counter.builder("tokentrim.cache.lookups").tag("result", "bypass").register(registry).increment();
            bypassCount.increment();
        }
        requestCount.increment();
        originalSum.add(original);
        sentSum.add(sent);
        savedSum.add(saved);
        costSum.add(costSavedUsd);
        this.latencyNanos.add(latencyNanos);
    }

    public void recordUpstream(long nanos) {
        upstreamLatency.record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recordQuality(double similarity) {
        quality.record(similarity);
    }

    public void recordFailure(long nanos) {
        failures.increment();
        failureCount.increment();
        requestLatency.record(nanos, TimeUnit.NANOSECONDS);
        this.latencyNanos.add(nanos);
        requestCount.increment();
        requests.increment();
    }

    public StatsSnapshot snapshot() {
        long hits = hitCount.sum();
        long misses = missCount.sum();
        long lookups = hits + misses;
        double rate = lookups == 0 ? 0d : (double) hits / lookups;
        long count = requestCount.sum();
        double average = count == 0 ? 0d : (latencyNanos.sum() / 1_000_000d) / count;
        return new StatsSnapshot(
                count,
                hits,
                misses,
                bypassCount.sum(),
                rate,
                originalSum.sum(),
                sentSum.sum(),
                savedSum.sum(),
                costSum.sum(),
                average,
                failureCount.sum()
        );
    }

    public void reset() {
        requestCount.reset();
        hitCount.reset();
        missCount.reset();
        bypassCount.reset();
        originalSum.reset();
        sentSum.reset();
        savedSum.reset();
        failureCount.reset();
        latencyNanos.reset();
        costSum.reset();
    }
}
