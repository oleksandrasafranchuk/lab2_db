package com.example.demo.nosql.bench;

import java.time.OffsetDateTime;
import java.util.List;

public record BenchmarkReport(
        OffsetDateTime startedAt,
        int receiptsLoaded,
        int iterations,
        int warmup,
        long preparationMs,
        List<ScenarioComparison> scenarios
) {
}
