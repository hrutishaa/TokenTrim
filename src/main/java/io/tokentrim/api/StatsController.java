package io.tokentrim.api;

import io.tokentrim.agent.RunLedger;
import io.tokentrim.api.dto.StatsSnapshot;
import io.tokentrim.metrics.TokenTrimMetrics;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/tokentrim", produces = MediaType.APPLICATION_JSON_VALUE)
public class StatsController {

    private final TokenTrimMetrics metrics;
    private final RunLedger runs;

    public StatsController(TokenTrimMetrics metrics, RunLedger runs) {
        this.metrics = metrics;
        this.runs = runs;
    }

    @GetMapping("/stats")
    public StatsSnapshot stats() {
        return metrics.snapshot();
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<?> run(@PathVariable String id) {
        return runs.find(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Errors.of(
                        "No agent run is recorded for " + id,
                        "invalid_request_error")));
    }
}
