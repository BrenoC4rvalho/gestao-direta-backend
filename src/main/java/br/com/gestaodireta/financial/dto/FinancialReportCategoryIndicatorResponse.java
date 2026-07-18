package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialReportCategoryIndicatorResponse(
        Long categoryId, String categoryName, BigDecimal amount) {}
