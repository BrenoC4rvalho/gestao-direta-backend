package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record MonthlyFinancialSummaryResponse(
        BigDecimal income, BigDecimal expense, BigDecimal balance) {}
