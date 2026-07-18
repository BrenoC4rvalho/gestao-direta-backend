package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialEvolutionPointResponse(
        String period,
        String label,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal netBalance,
        long transactionCount) {}
