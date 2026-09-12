package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestSeasonBudgetExecutionResponse(
        BigDecimal executedCost, BigDecimal plannedCost, BigDecimal percentage) {}
