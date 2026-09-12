package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialReportSummaryWithComparison(
        FinancialReportSummaryResponse summary,
        BigDecimal previousRealizedIncome,
        BigDecimal previousRealizedExpense,
        long previousRealizedTransactionCount) {}
