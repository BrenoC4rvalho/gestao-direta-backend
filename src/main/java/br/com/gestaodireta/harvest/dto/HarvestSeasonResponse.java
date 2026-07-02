package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HarvestSeasonResponse(
        Long id,
        Long farmId,
        String farmName,
        Long productionActivityId,
        String productionActivityName,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal expectedRevenue,
        BigDecimal expectedCost,
        BigDecimal areaHectares,
        HarvestSeasonStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
