package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialHarvestSummaryResponse(
        Long harvestSeasonId,
        String harvestSeasonName,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal profit,
        BigDecimal marginPercentage,
        long transactionCount) {}
