package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialCashFlowOpeningResponse(
        BigDecimal expectedBalance, BigDecimal projectedBalance) {}
