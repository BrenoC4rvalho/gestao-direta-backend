package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialHarvestSummaryDetails(
        BigDecimal resultPerHectare,
        BigDecimal periodRevenueShare,
        FinancialHarvestPlanningComparisonResponse incomeComparison,
        FinancialHarvestPlanningComparisonResponse expenseComparison,
        FinancialHarvestPlanningComparisonResponse resultComparison) {}
