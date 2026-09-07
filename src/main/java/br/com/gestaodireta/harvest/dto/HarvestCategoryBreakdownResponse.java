package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;
import java.util.List;

public record HarvestCategoryBreakdownResponse(
        BigDecimal total, List<HarvestCategoryAmountResponse> categories) {}
