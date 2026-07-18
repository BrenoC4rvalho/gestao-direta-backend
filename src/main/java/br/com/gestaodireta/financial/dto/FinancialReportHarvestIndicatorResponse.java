package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialReportHarvestIndicatorResponse(
        Long harvestSeasonId, String harvestSeasonName, BigDecimal profit) {}
