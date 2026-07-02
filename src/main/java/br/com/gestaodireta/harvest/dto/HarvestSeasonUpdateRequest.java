package br.com.gestaodireta.harvest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record HarvestSeasonUpdateRequest(
        @NotNull Long productionActivityId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 500) String description,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @DecimalMin("0.00") BigDecimal expectedRevenue,
        @DecimalMin("0.00") BigDecimal expectedCost,
        @DecimalMin("0.00") BigDecimal areaHectares) {}
