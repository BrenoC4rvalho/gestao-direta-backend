package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestRealizedSummaryResponse(
        BigDecimal realizedCost, BigDecimal realizedRevenue, BigDecimal realizedProfit) {}
