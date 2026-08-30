package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialReportCommitmentsResponse(
        BigDecimal accountsReceivable,
        BigDecimal accountsPayable,
        BigDecimal overdueReceivableAmount,
        Long overdueReceivableCount,
        BigDecimal overduePayableAmount,
        Long overduePayableCount,
        BigDecimal next30DaysReceivable,
        BigDecimal next30DaysPayable) {}
