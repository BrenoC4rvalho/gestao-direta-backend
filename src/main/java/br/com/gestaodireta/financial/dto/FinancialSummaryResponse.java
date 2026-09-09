package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialSummaryResponse(
        Long farmId,
        BigDecimal currentBalance,
        BigDecimal totalReceivable,
        BigDecimal totalPayable,
        BigDecimal overduePayable,
        int horizonDays,
        BigDecimal receivableInHorizon,
        BigDecimal payableInHorizon,
        BigDecimal projectedBalance,
        FinancialCoverageResponse financialCoverage) {}
