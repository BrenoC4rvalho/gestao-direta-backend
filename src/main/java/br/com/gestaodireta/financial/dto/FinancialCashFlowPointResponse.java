package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialCashFlowPointResponse(
        String period,
        String label,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal realizedIncome,
        BigDecimal realizedExpense,
        BigDecimal projectedIncome,
        BigDecimal projectedExpense,
        BigDecimal overdueIncome,
        BigDecimal overdueExpense,
        BigDecimal expectedBalance,
        BigDecimal projectedBalance,
        boolean currentPeriod) {}
