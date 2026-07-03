package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestSeasonSummaryResponse(
        Long harvestSeasonId,
        String harvestSeasonName,
        Long productionActivityId,
        String productionActivityName,
        Long farmId,
        String farmName,
        BigDecimal expectedCost,
        BigDecimal expectedRevenue,
        BigDecimal expectedProfit,
        BigDecimal realizedCost,
        BigDecimal realizedRevenue,
        BigDecimal realizedProfit,
        BigDecimal pendingExpenses,
        BigDecimal overdueExpenses,
        BigDecimal pendingRevenue,
        Long transactionCount,
        Long incomeCount,
        Long expenseCount,
        BigDecimal areaHectares,
        BigDecimal costPerHectare,
        BigDecimal revenuePerHectare,
        BigDecimal profitPerHectare) {}
