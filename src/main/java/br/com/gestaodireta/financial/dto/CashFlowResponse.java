package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowResponse(
        Long farmId,
        Integer year,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        List<CashFlowPointResponse> points) {}
