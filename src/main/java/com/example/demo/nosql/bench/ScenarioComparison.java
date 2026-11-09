package com.example.demo.nosql.bench;

public record ScenarioComparison(
        String scenario,
        String description,
        String sqlPlan,
        String mongoPlan,
        Measurement postgres,
        Measurement mongodb,
        double mongoSpeedup,
        String faster
) {
}
