package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestPlanningSummaryResponse(
        BigDecimal plannedCost,
        BigDecimal plannedRevenue,
        BigDecimal plannedProfit,
        BigDecimal plannedMargin) {}
