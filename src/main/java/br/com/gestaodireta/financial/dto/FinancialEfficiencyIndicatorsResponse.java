package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialEfficiencyIndicatorsResponse(
        BigDecimal costToIncomePercentage, BigDecimal returnOnCostsPercentage) {}
