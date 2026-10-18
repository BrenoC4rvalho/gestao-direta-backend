package br.com.gestaodireta.financial.dto;

public record FinancialCommitmentStatusResponse(
        FinancialCommitmentHistoryResponse history,
        FinancialCommitmentCurrentResponse current,
        FinancialCommitmentUpcomingResponse upcoming) {}
