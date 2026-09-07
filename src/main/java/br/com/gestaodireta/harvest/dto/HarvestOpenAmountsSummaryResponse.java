package br.com.gestaodireta.harvest.dto;

import java.math.BigDecimal;

public record HarvestOpenAmountsSummaryResponse(
        BigDecimal payableAmount,
        BigDecimal receivableAmount,
        FinancialAmountSummaryResponse pending,
        FinancialAmountSummaryResponse overdue) {}
