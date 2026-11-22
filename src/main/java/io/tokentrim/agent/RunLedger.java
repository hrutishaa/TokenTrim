package io.tokentrim.agent;

import io.tokentrim.config.TokenTrimProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory totals for {@code X-TokenTrim-Run-Id}. */
public final class RunLedger {

    private final TokenTrimProperties properties;
    private final ConcurrentHashMap<String, Run> runs = new ConcurrentHashMap<>();

    public RunLedger(TokenTrimProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.getAgent().isEnabled() && properties.getAgent().getRunBudget().isEnabled();
    }

    public long projectedSent(String runId, int additionalSent) {
        Run run = runs.get(runId);
        long already = run == null ? 0 : run.sent;
        return already + Math.max(0, additionalSent);
    }

    public void checkHardLimit(String runId, int additionalSent) {
        if (!enabled() || runId == null || runId.isBlank()) {
            return;
        }
        long projected = projectedSent(runId, additionalSent);
        long hard = properties.getAgent().getRunBudget().getHardLimitTokens();
        if (hard > 0 && projected > hard) {
            throw new RunBudgetExceededException(runId, projected, hard);
        }
    }

    public boolean overSoftLimit(String runId, int additionalSent) {
        if (!enabled() || runId == null || runId.isBlank()) {
            return false;
        }
        long soft = properties.getAgent().getRunBudget().getSoftLimitTokens();
        return soft > 0 && projectedSent(runId, additionalSent) > soft;
    }

    public synchronized RunSnapshot record(
            String runId,
            int original,
            int sent,
            int saved,
            Map<String, Integer> techniques
    ) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        Run run = runs.computeIfAbsent(runId, ignored -> new Run());
        run.requests++;
        run.original += Math.max(0, original);
        run.sent += Math.max(0, sent);
        run.saved += Math.max(0, saved);
        if (techniques != null) {
            techniques.forEach((name, value) -> run.techniques.merge(name, (long) Math.max(0, value), Long::sum));
        }
        run.updatedAtMs = System.currentTimeMillis();
        return snapshot(runId, run);
    }

    public Optional<RunSnapshot> find(String runId) {
        Run run = runs.get(runId);
        if (run == null) {
            return Optional.empty();
        }
        return Optional.of(snapshot(runId, run));
    }

    public void clear() {
        runs.clear();
    }

    private RunSnapshot snapshot(String runId, Run run) {
        TokenTrimProperties.RunBudget budget = properties.getAgent().getRunBudget();
        boolean overSoft = budget.getSoftLimitTokens() > 0 && run.sent > budget.getSoftLimitTokens();
        return new RunSnapshot(
                runId,
                run.requests,
                run.original,
                run.sent,
                run.saved,
                Map.copyOf(run.techniques),
                budget.getSoftLimitTokens(),
                budget.getHardLimitTokens(),
                overSoft,
                run.updatedAtMs);
    }

    private static final class Run {
        private long requests;
        private long original;
        private long sent;
        private long saved;
        private long updatedAtMs;
        private final Map<String, Long> techniques = new LinkedHashMap<>();
    }
}
