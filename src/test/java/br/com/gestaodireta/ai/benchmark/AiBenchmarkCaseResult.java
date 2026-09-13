package br.com.gestaodireta.ai.benchmark;

import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;

record AiBenchmarkCaseResult(
        AiBenchmarkCase benchmarkCase,
        String provider,
        String model,
        AiBenchmarkResultType resultType,
        boolean criticalFailure,
        String failureReason,
        AiBenchmarkActual actual) {

    FinancialTransactionExtractionResult extraction() {
        return actual.extraction();
    }
}
