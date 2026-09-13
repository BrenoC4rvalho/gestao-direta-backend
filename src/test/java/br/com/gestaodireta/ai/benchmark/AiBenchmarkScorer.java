package br.com.gestaodireta.ai.benchmark;

import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import java.math.BigDecimal;
import java.util.Locale;

final class AiBenchmarkScorer {

    AiBenchmarkCaseResult score(
            AiBenchmarkCase benchmarkCase,
            String provider,
            String model,
            AiBenchmarkActual actual) {
        if (actual.technicalError() != null) {
            return result(
                    benchmarkCase,
                    provider,
                    model,
                    AiBenchmarkResultType.TECHNICAL_ERROR,
                    false,
                    actual.technicalError(),
                    actual);
        }

        if (!benchmarkCase.shouldCreate()) {
            return scoreBlocked(benchmarkCase, provider, model, actual);
        }
        if (!actual.createdPending()) {
            return result(
                    benchmarkCase,
                    provider,
                    model,
                    AiBenchmarkResultType.SHOULD_HAVE_CREATED,
                    false,
                    actual.blockedReason(),
                    actual);
        }
        FinancialTransactionExtractionResult extraction = actual.extraction();
        if (extraction == null) {
            return result(
                    benchmarkCase,
                    provider,
                    model,
                    AiBenchmarkResultType.WRONG_FIELDS,
                    false,
                    "A pendência foi criada sem resultado extraído capturado.",
                    actual);
        }
        if (benchmarkCase.expectedType() != null
                && benchmarkCase.expectedType() != extraction.type()) {
            return result(
                    benchmarkCase,
                    provider,
                    model,
                    AiBenchmarkResultType.WRONG_TYPE,
                    true,
                    "Tipo extraído diferente do esperado.",
                    actual);
        }
        if (benchmarkCase.expectedAmount() != null
                && !sameAmount(benchmarkCase.expectedAmount(), extraction.amount())) {
            return result(
                    benchmarkCase,
                    provider,
                    model,
                    AiBenchmarkResultType.WRONG_AMOUNT,
                    true,
                    "Valor extraído diferente do esperado.",
                    actual);
        }
        if (!descriptionMatches(benchmarkCase.expectedDescriptionContains(), extraction)) {
            return result(
                    benchmarkCase,
                    provider,
                    model,
                    AiBenchmarkResultType.WRONG_FIELDS,
                    false,
                    "Descrição extraída não contém o contexto esperado.",
                    actual);
        }
        if (!categoryMatches(benchmarkCase.expectedCategory(), extraction)) {
            return result(
                    benchmarkCase,
                    provider,
                    model,
                    AiBenchmarkResultType.WRONG_FIELDS,
                    false,
                    "Categoria extraída diferente da esperada.",
                    actual);
        }
        return result(
                benchmarkCase,
                provider,
                model,
                AiBenchmarkResultType.CORRECT_CREATED,
                false,
                null,
                actual);
    }

    private AiBenchmarkCaseResult scoreBlocked(
            AiBenchmarkCase benchmarkCase,
            String provider,
            String model,
            AiBenchmarkActual actual) {
        if (actual.createdPending()) {
            boolean inventedAmount =
                    benchmarkCase.expectedAmount() == null
                            && actual.extraction() != null
                            && actual.extraction().amount() != null;
            AiBenchmarkResultType type =
                    inventedAmount
                            ? AiBenchmarkResultType.HALLUCINATED_DATA
                            : AiBenchmarkResultType.SHOULD_HAVE_BLOCKED;
            return result(
                    benchmarkCase,
                    provider,
                    model,
                    type,
                    true,
                    inventedAmount
                            ? "A aplicação aceitou uma movimentação com valor não declarado."
                            : "A aplicação criou pendência para caso que deveria bloquear.",
                    actual);
        }
        AiBenchmarkResultType type =
                benchmarkCase.expectedOutcome() == AiBenchmarkExpectedOutcome.REJECT_INVALID
                        ? AiBenchmarkResultType.CORRECT_REJECTED_INVALID
                        : AiBenchmarkResultType.CORRECT_BLOCKED_INCOMPLETE;
        return result(benchmarkCase, provider, model, type, false, actual.blockedReason(), actual);
    }

    private boolean sameAmount(BigDecimal expected, BigDecimal actual) {
        return actual != null && expected.compareTo(actual) == 0;
    }

    private boolean descriptionMatches(
            String expectedDescriptionContains, FinancialTransactionExtractionResult extraction) {
        if (expectedDescriptionContains == null) {
            return true;
        }
        return extraction.description() != null
                && extraction
                        .description()
                        .toLowerCase(Locale.ROOT)
                        .contains(expectedDescriptionContains.toLowerCase(Locale.ROOT));
    }

    private boolean categoryMatches(
            String expectedCategory, FinancialTransactionExtractionResult extraction) {
        return expectedCategory == null
                || (extraction.categoryName() != null
                        && expectedCategory.equalsIgnoreCase(extraction.categoryName().trim()));
    }

    private AiBenchmarkCaseResult result(
            AiBenchmarkCase benchmarkCase,
            String provider,
            String model,
            AiBenchmarkResultType type,
            boolean critical,
            String reason,
            AiBenchmarkActual actual) {
        return new AiBenchmarkCaseResult(
                benchmarkCase, provider, model, type, critical, reason, actual);
    }
}
