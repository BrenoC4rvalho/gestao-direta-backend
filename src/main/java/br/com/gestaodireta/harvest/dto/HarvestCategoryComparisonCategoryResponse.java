package br.com.gestaodireta.harvest.dto;

import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import br.com.gestaodireta.harvest.enumeration.HarvestCategoryComparisonStatus;
import java.math.BigDecimal;

public record HarvestCategoryComparisonCategoryResponse(
        Long categoryId,
        String categoryName,
        boolean planned,
        BigDecimal plannedAmount,
        BigDecimal realizedAmount,
        BigDecimal difference,
        BigDecimal percentageDifference,
        HarvestCategoryComparisonStatus status,
        ComparisonSemantic semantic) {}
