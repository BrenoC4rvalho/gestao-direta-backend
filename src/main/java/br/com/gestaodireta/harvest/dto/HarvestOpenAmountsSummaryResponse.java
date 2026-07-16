package br.com.gestaodireta.harvest.dto;

public record HarvestOpenAmountsSummaryResponse(
        FinancialAmountSummaryResponse pending, FinancialAmountSummaryResponse overdue) {}
