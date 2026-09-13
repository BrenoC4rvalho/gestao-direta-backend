package br.com.gestaodireta.ai.benchmark;

import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;

record AiBenchmarkActual(
        FinancialTransactionExtractionResult extraction,
        boolean createdPending,
        String blockedReason,
        String technicalError,
        long elapsedMillis,
        String rawResponse) {}
