package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialRuralManagementIndicatorsResponse(
        BigDecimal areaHectares,
        BigDecimal incomePerHectare,
        BigDecimal costPerHectare,
        BigDecimal resultPerHectare) {}
