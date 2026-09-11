package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialResultIndicatorsResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal projectedResult,
        BigDecimal marginPercentage,
        BigDecimal realizedIncome,
        BigDecimal realizedExpense,
        BigDecimal realizedResult) {}
