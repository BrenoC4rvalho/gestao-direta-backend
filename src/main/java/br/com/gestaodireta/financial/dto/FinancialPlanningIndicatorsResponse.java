package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialPlanningAvailability;
import java.math.BigDecimal;

public record FinancialPlanningIndicatorsResponse(
        FinancialPlanningAvailability availability,
        BigDecimal incomeExecutionPercentage,
        BigDecimal expenseExecutionPercentage,
        BigDecimal incomeDeviation,
        BigDecimal expenseDeviation) {}
