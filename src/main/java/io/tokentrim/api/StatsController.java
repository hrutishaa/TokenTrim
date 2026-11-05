package io.tokentrim.api;

import io.tokentrim.api.dto.StatsSnapshot;
import io.tokentrim.metrics.TokenTrimMetrics;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/tokentrim", produces = MediaType.APPLICATION_JSON_VALUE)
public class StatsController {

    private final TokenTrimMetrics metrics;

    public StatsController(TokenTrimMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/stats")
    public StatsSnapshot stats() {
        return metrics.snapshot();
    }
}
