package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.util.List;

public record HarvestSeasonBudgetCategoryResponse(
        Long categoryId,
        String categoryName,
        TransactionType type,
        long itemCount,
        BigDecimal plannedAmount,
        List<HarvestSeasonBudgetItemResponse> items) {}
