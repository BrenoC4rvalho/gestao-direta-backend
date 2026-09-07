package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record HarvestSeasonComparisonHarvestResponse(
        Long id,
        String name,
        HarvestSeasonStatus status,
        String productionActivityName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal areaHectares,
        HarvestPlanningSummaryResponse planning,
        HarvestProjectionSummaryResponse projection,
        HarvestRealizedSummaryResponse realized,
        HarvestSeasonPerHectareComparisonResponse perHectare) {}
