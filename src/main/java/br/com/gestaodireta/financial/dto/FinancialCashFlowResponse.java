package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;
import java.util.List;

public record FinancialCashFlowResponse(
        BigDecimal openingExpectedBalance,
        BigDecimal openingProjectedBalance,
        List<FinancialCashFlowPointResponse> points) {}
