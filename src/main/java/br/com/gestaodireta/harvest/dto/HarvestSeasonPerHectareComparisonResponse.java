package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestSeasonPerHectareComparisonResponse(
        BigDecimal plannedCostPerHectare,
        BigDecimal plannedRevenuePerHectare,
        BigDecimal plannedResultPerHectare,
        BigDecimal projectedCostPerHectare,
        BigDecimal projectedRevenuePerHectare,
        BigDecimal projectedProfitPerHectare,
        BigDecimal realizedCostPerHectare,
        BigDecimal realizedRevenuePerHectare,
        BigDecimal realizedProfitPerHectare) {}
