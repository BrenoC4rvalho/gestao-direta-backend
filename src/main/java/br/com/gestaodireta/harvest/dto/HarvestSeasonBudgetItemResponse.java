package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;

public record HarvestSeasonBudgetItemResponse(
        Long id,
        Long harvestSeasonId,
        Long categoryId,
        String categoryName,
        TransactionType type,
        String description,
        BigDecimal plannedAmount) {}
