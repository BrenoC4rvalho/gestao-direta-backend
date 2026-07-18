package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialReportSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netBalance,
        BigDecimal marginPercentage,
        BigDecimal realizedIncome,
        BigDecimal realizedExpense,
        BigDecimal projectedIncome,
        BigDecimal projectedExpense) {}
