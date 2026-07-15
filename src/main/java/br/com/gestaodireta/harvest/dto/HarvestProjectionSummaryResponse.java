package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestProjectionSummaryResponse(
        BigDecimal projectedCost, BigDecimal projectedRevenue, BigDecimal projectedProfit) {}
