package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialSummaryResponse(
        Long farmId,
        BigDecimal incomeTotal,
        BigDecimal expenseTotal,
        BigDecimal balance,
        BigDecimal pendingTotal,
        BigDecimal paidTotal,
        BigDecimal overdueTotal) {}
