package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;

public record DashboardHarvestSeasonResponse(
        Long id,
        Long farmId,
        String name,
        HarvestSeasonStatus status,
        Long productionActivityId,
        String productionActivityName,
        DashboardHarvestFinancialValuesResponse realized,
        DashboardHarvestFinancialValuesResponse projection,
        FinancialAmountCountResponse dueNext7Days,
        FinancialAmountCountResponse overdue) {}
