package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HarvestSeasonSummaryListResponse(
        Long id,
        Long farmId,
        String farmName,
        Long productionActivityId,
        String productionActivityName,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal expectedCost,
        BigDecimal expectedRevenue,
        BigDecimal expectedProfit,
        BigDecimal areaHectares,
        HarvestSeasonStatus status,
        BigDecimal realizedCost,
        BigDecimal realizedRevenue,
        BigDecimal realizedProfit,
        BigDecimal pendingExpenses,
        BigDecimal overdueExpenses,
        BigDecimal pendingRevenue,
        Long transactionCount,
        Long incomeCount,
        Long expenseCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
