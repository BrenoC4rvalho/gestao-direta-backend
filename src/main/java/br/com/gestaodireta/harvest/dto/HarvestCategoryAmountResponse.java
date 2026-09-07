package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestCategoryAmountResponse(
        Long categoryId, String categoryName, BigDecimal amount, BigDecimal percentage) {}
