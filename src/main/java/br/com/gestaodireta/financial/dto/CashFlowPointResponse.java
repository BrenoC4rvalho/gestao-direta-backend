package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record CashFlowPointResponse(
        Integer month,
        String label,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal netFlow,
        BigDecimal balance) {}
