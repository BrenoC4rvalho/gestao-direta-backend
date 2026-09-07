package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;
import java.util.List;

public record HarvestCategoryComparisonBreakdownResponse(
        BigDecimal plannedTotal,
        BigDecimal realizedTotal,
        BigDecimal difference,
        List<HarvestCategoryComparisonCategoryResponse> categories) {}
