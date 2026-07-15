package br.com.gestaodireta.harvest.dto;

public record HarvestSeasonFinancialSummaryResponse(
        Long farmId,
        Long activeHarvestCount,
        HarvestPlanningSummaryResponse planning,
        HarvestRealizedSummaryResponse realized,
        HarvestProjectionSummaryResponse projection,
        HarvestComparisonSummaryResponse comparison) {}
