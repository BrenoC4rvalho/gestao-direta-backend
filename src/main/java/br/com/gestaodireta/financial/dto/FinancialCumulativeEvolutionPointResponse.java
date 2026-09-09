package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialCumulativeEvolutionPointResponse(
        String period,
        String label,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal cumulativeIncome,
        BigDecimal cumulativeExpense) {}
