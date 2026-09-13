package br.com.gestaodireta.ai.benchmark;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class AiBenchmarkThresholds {
    private final Double completeAccuracy;
    private final Double invalidRejection;
    private final Double incompleteBlocking;
    private final Double hallucinationRate;

    private AiBenchmarkThresholds(
            Double completeAccuracy,
            Double invalidRejection,
            Double incompleteBlocking,
            Double hallucinationRate) {
        this.completeAccuracy = completeAccuracy;
        this.invalidRejection = invalidRejection;
        this.incompleteBlocking = incompleteBlocking;
        this.hallucinationRate = hallucinationRate;
    }

    static AiBenchmarkThresholds fromSystemProperties() {
        return new AiBenchmarkThresholds(
                number("ai.benchmark.threshold.complete-accuracy"),
                number("ai.benchmark.threshold.invalid-rejection"),
                number("ai.benchmark.threshold.incomplete-blocking"),
                number("ai.benchmark.threshold.max-hallucination-rate"));
    }

    void assertSatisfied(List<AiBenchmarkCaseResult> results) {
        Map<String, AiBenchmarkMetrics> metrics =
                results.stream()
                        .collect(
                                Collectors.groupingBy(
                                        AiBenchmarkCaseResult::provider,
                                        Collectors.collectingAndThen(
                                                Collectors.toList(),
                                                values ->
                                                        AiBenchmarkMetrics.from(
                                                                values.getFirst().provider(),
                                                                values))));
        for (AiBenchmarkMetrics metric : metrics.values()) {
            requireAtLeast(
                    metric.provider(),
                    "completeAccuracy",
                    metric.completeAccuracy(),
                    completeAccuracy);
            requireAtLeast(
                    metric.provider(),
                    "invalidRejection",
                    metric.invalidRejectionAccuracy(),
                    invalidRejection);
            requireAtLeast(
                    metric.provider(),
                    "incompleteBlocking",
                    metric.incompleteBlockingAccuracy(),
                    incompleteBlocking);
            if (hallucinationRate != null && metric.hallucinationRate() > hallucinationRate) {
                throw new AssertionError(
                        metric.provider() + " hallucinationRate exceeds configured threshold");
            }
        }
    }

    private void requireAtLeast(String provider, String name, double value, Double threshold) {
        if (threshold != null && value < threshold) {
            throw new AssertionError(provider + " " + name + " is below configured threshold");
        }
    }

    private static Double number(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? null : Double.valueOf(value);
    }
}
