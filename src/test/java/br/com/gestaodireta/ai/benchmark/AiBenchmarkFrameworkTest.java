package br.com.gestaodireta.ai.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiBenchmarkFrameworkTest {
    private final AiBenchmarkScorer scorer = new AiBenchmarkScorer();

    @Test
    void shouldLoadTheRequiredDatasetDistribution() {
        List<AiBenchmarkCase> cases = new AiBenchmarkDataset().load();

        assertThat(cases).hasSize(150);
        assertThat(cases).filteredOn(item -> item.group() == AiBenchmarkGroup.ENTRY).hasSize(50);
        assertThat(cases).filteredOn(item -> item.group() == AiBenchmarkGroup.EXIT).hasSize(50);
        assertThat(cases).filteredOn(item -> item.group() == AiBenchmarkGroup.INVALID).hasSize(20);
        assertThat(cases)
                .filteredOn(item -> item.group() == AiBenchmarkGroup.INCOMPLETE)
                .hasSize(30);
    }

    @Test
    void shouldIdentifyInventedAmountAsCriticalHallucination() {
        AiBenchmarkCase item =
                new AiBenchmarkCase(
                        "INCOMPLETE-01",
                        "Paguei o adubo.",
                        AiBenchmarkGroup.INCOMPLETE,
                        AiBenchmarkExpectedOutcome.BLOCK_INCOMPLETE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        List.of("amount"));

        AiBenchmarkCaseResult result =
                scorer.score(
                        item,
                        "gemini",
                        "test",
                        actual(
                                true,
                                new FinancialTransactionExtractionResult(
                                        true,
                                        TransactionType.EXPENSE,
                                        new BigDecimal("500"),
                                        null,
                                        "Adubo",
                                        null,
                                        BigDecimal.ONE,
                                        List.of())));

        assertThat(result.resultType()).isEqualTo(AiBenchmarkResultType.HALLUCINATED_DATA);
        assertThat(result.criticalFailure()).isTrue();
    }

    @Test
    void shouldCompareAmountsByNumericValueAndRejectWrongType() {
        AiBenchmarkCase item = completeCase();
        AiBenchmarkCaseResult amountMatch =
                scorer.score(
                        item,
                        "ollama",
                        "test",
                        actual(
                                true,
                                extraction(
                                        TransactionType.INCOME,
                                        new BigDecimal("1250.00"),
                                        "Venda de café")));
        AiBenchmarkCaseResult wrongType =
                scorer.score(
                        item,
                        "ollama",
                        "test",
                        actual(
                                true,
                                extraction(
                                        TransactionType.EXPENSE,
                                        new BigDecimal("1250"),
                                        "Venda de café")));

        assertThat(amountMatch.resultType()).isEqualTo(AiBenchmarkResultType.CORRECT_CREATED);
        assertThat(wrongType.resultType()).isEqualTo(AiBenchmarkResultType.WRONG_TYPE);
        assertThat(wrongType.criticalFailure()).isTrue();
    }

    @Test
    void shouldCalculateMetricsAndApplyOnlyConfiguredThresholds() {
        AiBenchmarkCase entry = completeCase();
        AiBenchmarkCase invalid =
                new AiBenchmarkCase(
                        "INVALID-01",
                        "céu bonito",
                        AiBenchmarkGroup.INVALID,
                        AiBenchmarkExpectedOutcome.REJECT_INVALID,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        List.of());
        List<AiBenchmarkCaseResult> results =
                List.of(
                        scorer.score(
                                entry,
                                "gemini",
                                "test",
                                actual(
                                        true,
                                        extraction(
                                                TransactionType.INCOME,
                                                new BigDecimal("1250"),
                                                "Venda de café"))),
                        scorer.score(invalid, "gemini", "test", actual(false, null)));

        AiBenchmarkMetrics metrics = AiBenchmarkMetrics.from("gemini", results);

        assertThat(metrics.completeAccuracy()).isEqualTo(100);
        assertThat(metrics.invalidRejectionAccuracy()).isEqualTo(100);
        assertThat(metrics.p95LatencyMillis()).isZero();
        System.setProperty("ai.benchmark.threshold.complete-accuracy", "101");
        try {
            assertThatThrownBy(
                            () ->
                                    AiBenchmarkThresholds.fromSystemProperties()
                                            .assertSatisfied(results))
                    .isInstanceOf(AssertionError.class);
        } finally {
            System.clearProperty("ai.benchmark.threshold.complete-accuracy");
        }
    }

    private AiBenchmarkCase completeCase() {
        return new AiBenchmarkCase(
                "ENTRY-01",
                "Recebi R$ 1.250 pela venda de café.",
                AiBenchmarkGroup.ENTRY,
                AiBenchmarkExpectedOutcome.CREATE_PENDING,
                TransactionType.INCOME,
                new BigDecimal("1250"),
                "venda",
                null,
                null,
                true,
                true,
                List.of());
    }

    private AiBenchmarkActual actual(
            boolean created, FinancialTransactionExtractionResult extraction) {
        return new AiBenchmarkActual(extraction, created, null, null, 0, null);
    }

    private FinancialTransactionExtractionResult extraction(
            TransactionType type, BigDecimal amount, String description) {
        return new FinancialTransactionExtractionResult(
                true, type, amount, null, description, null, new BigDecimal("0.95"), List.of());
    }
}
