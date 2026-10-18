package br.com.gestaodireta.financial.dto;

public record FinancialCommitmentUpcomingResponse(
        Integer days,
        FinancialCommitmentAmountResponse payables,
        FinancialCommitmentAmountResponse receivables) {}
