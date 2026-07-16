package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record FinancialAmountSummaryResponse(
        BigDecimal payableAmount, BigDecimal receivableAmount) {}
