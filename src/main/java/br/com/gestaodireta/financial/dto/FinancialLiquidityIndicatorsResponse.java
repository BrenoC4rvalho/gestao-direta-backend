package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialLiquidityIndicatorsResponse(
        BigDecimal accountsReceivable,
        BigDecimal accountsPayable,
        BigDecimal overdueReceivable,
        BigDecimal overduePayable,
        BigDecimal coveragePercentage,
        BigDecimal cashNeed) {}
