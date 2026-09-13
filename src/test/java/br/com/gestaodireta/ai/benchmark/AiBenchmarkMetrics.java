package br.com.gestaodireta.ai.benchmark;

import java.util.Comparator;
import java.util.List;

record AiBenchmarkMetrics(
        String provider,
        int total,
        int completeCorrect,
        int completeTotal,
        int entryCorrect,
        int entryTotal,
        int exitCorrect,
        int exitTotal,
        int incompleteCorrect,
        int incompleteTotal,
        int invalidCorrect,
        int invalidTotal,
        int hallucinations,
        int technicalErrors,
        int criticalFailures,
        double averageLatencyMillis,
        long p50LatencyMillis,
        long p95LatencyMillis) {

    static AiBenchmarkMetrics from(String provider, List<AiBenchmarkCaseResult> results) {
        return new AiBenchmarkMetrics(
                provider,
                results.size(),
                correct(results, AiBenchmarkGroup.ENTRY) + correct(results, AiBenchmarkGroup.EXIT),
                count(results, AiBenchmarkGroup.ENTRY) + count(results, AiBenchmarkGroup.EXIT),
                correct(results, AiBenchmarkGroup.ENTRY),
                count(results, AiBenchmarkGroup.ENTRY),
                correct(results, AiBenchmarkGroup.EXIT),
                count(results, AiBenchmarkGroup.EXIT),
                correct(results, AiBenchmarkGroup.INCOMPLETE),
                count(results, AiBenchmarkGroup.INCOMPLETE),
                correct(results, AiBenchmarkGroup.INVALID),
                count(results, AiBenchmarkGroup.INVALID),
                countResult(results, AiBenchmarkResultType.HALLUCINATED_DATA),
                countResult(results, AiBenchmarkResultType.TECHNICAL_ERROR),
                (int) results.stream().filter(AiBenchmarkCaseResult::criticalFailure).count(),
                average(results),
                percentile(results, 0.50),
                percentile(results, 0.95));
    }

    double completeAccuracy() {
        return percentage(completeCorrect, completeTotal);
    }

    double incompleteBlockingAccuracy() {
        return percentage(incompleteCorrect, incompleteTotal);
    }

    double invalidRejectionAccuracy() {
        return percentage(invalidCorrect, invalidTotal);
    }

    double hallucinationRate() {
        return percentage(hallucinations, total);
    }

    private static int correct(List<AiBenchmarkCaseResult> results, AiBenchmarkGroup group) {
        return (int)
                results.stream()
                        .filter(result -> result.benchmarkCase().group() == group)
                        .filter(
                                result ->
                                        result.resultType() == AiBenchmarkResultType.CORRECT_CREATED
                                                || result.resultType()
                                                        == AiBenchmarkResultType
                                                                .CORRECT_BLOCKED_INCOMPLETE
                                                || result.resultType()
                                                        == AiBenchmarkResultType
                                                                .CORRECT_REJECTED_INVALID)
                        .count();
    }

    private static int count(List<AiBenchmarkCaseResult> results, AiBenchmarkGroup group) {
        return (int)
                results.stream().filter(result -> result.benchmarkCase().group() == group).count();
    }

    private static int countResult(
            List<AiBenchmarkCaseResult> results, AiBenchmarkResultType resultType) {
        return (int) results.stream().filter(result -> result.resultType() == resultType).count();
    }

    private static double average(List<AiBenchmarkCaseResult> results) {
        return results.stream()
                .mapToLong(result -> result.actual().elapsedMillis())
                .average()
                .orElse(0);
    }

    private static long percentile(List<AiBenchmarkCaseResult> results, double percentile) {
        List<Long> sorted =
                results.stream()
                        .map(result -> result.actual().elapsedMillis())
                        .sorted(Comparator.naturalOrder())
                        .toList();
        if (sorted.isEmpty()) {
            return 0;
        }
        return sorted.get((int) Math.ceil(percentile * sorted.size()) - 1);
    }

    private static double percentage(int numerator, int denominator) {
        return denominator == 0 ? 0 : numerator * 100.0 / denominator;
    }
}
