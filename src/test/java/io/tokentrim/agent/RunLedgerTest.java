package io.tokentrim.agent;

import io.tokentrim.config.TokenTrimProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunLedgerTest {

    @Test
    void tracksCumulativeTokensAndRejectsTheHardLimit() {
        TokenTrimProperties properties = TokenTrimProperties.createDefault();
        properties.getAgent().getRunBudget().setSoftLimitTokens(30);
        properties.getAgent().getRunBudget().setHardLimitTokens(50);
        RunLedger ledger = new RunLedger(properties);

        assertThat(ledger.overSoftLimit("run-1", 40)).isTrue();
        ledger.record("run-1", 40, 40, 0, Map.of("tool_compaction", 5));
        assertThat(ledger.find("run-1").orElseThrow().tokensSent()).isEqualTo(40);
        assertThat(ledger.find("run-1").orElseThrow().overSoftLimit()).isTrue();

        assertThatThrownBy(() -> ledger.checkHardLimit("run-1", 20))
                .isInstanceOf(RunBudgetExceededException.class);
        ledger.checkHardLimit("run-1", 10);
    }
}
