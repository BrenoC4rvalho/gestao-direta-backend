package br.com.gestaodireta.harvest.dto;

public record HarvestCategoryComparisonResponse(
        HarvestCategoryComparisonBreakdownResponse expenses,
        HarvestCategoryComparisonBreakdownResponse incomes) {}
