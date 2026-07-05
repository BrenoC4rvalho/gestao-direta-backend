package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialSummaryResponse(
        Long farmId,
        BigDecimal currentBalance,
        BigDecimal expectedIncome,
        BigDecimal expectedExpense,
        BigDecimal projectedBalance,
        BigDecimal payableNext30Days,
        BigDecimal overdueExpenses,
        BigDecimal receivableNext30Days,
        BigDecimal cashFlowNext30Days) {}
