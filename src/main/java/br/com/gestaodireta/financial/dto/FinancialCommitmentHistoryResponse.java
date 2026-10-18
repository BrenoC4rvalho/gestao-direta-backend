package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialCommitmentHistoryResponse(
        Long expensesOnTime,
        Long expensesLate,
        Long incomesOnTime,
        Long incomesLate,
        BigDecimal expenseOnTimeRate,
        BigDecimal incomeOnTimeRate,
        Long unclassifiableExpenses,
        Long unclassifiableIncomes) {}
