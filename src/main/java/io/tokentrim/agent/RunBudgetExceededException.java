package io.tokentrim.agent;

public class RunBudgetExceededException extends RuntimeException {

    private final String runId;
    private final long usedTokens;
    private final long limitTokens;

    public RunBudgetExceededException(String runId, long usedTokens, long limitTokens) {
        super("Run " + runId + " would use " + usedTokens
                + " tokens and the hard limit is " + limitTokens
                + ". Shorten the agent transcript or raise tokentrim.agent.run-budget.hard-limit-tokens.");
        this.runId = runId;
        this.usedTokens = usedTokens;
        this.limitTokens = limitTokens;
    }

    public String runId() {
        return runId;
    }

    public long usedTokens() {
        return usedTokens;
    }

    public long limitTokens() {
        return limitTokens;
    }
}
