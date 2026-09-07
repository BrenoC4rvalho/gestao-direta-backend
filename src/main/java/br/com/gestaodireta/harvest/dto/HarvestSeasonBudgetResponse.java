package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;
import java.util.List;

public record HarvestSeasonBudgetResponse(
        Long harvestSeasonId,
        BigDecimal plannedRevenue,
        BigDecimal plannedExpense,
        BigDecimal plannedResult,
        BigDecimal plannedMargin,
        List<HarvestSeasonBudgetCategoryResponse> expenses,
        List<HarvestSeasonBudgetCategoryResponse> incomes) {}
