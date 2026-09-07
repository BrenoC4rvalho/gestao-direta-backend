package br.com.gestaodireta.harvest.dto;

public record HarvestCategoryMovementsResponse(
        HarvestCategoryBreakdownResponse expenses, HarvestCategoryBreakdownResponse incomes) {}
