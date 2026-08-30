package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialReportCommitmentsResponse(
        BigDecimal accountsReceivable,
        BigDecimal accountsPayable,
        BigDecimal overdueReceivableAmount,
        Long overdueReceivableCount,
        BigDecimal overduePayableAmount,
        Long overduePayableCount,
        boolean next30DaysAvailable,
        BigDecimal next30DaysReceivable,
        BigDecimal next30DaysPayable) {}
