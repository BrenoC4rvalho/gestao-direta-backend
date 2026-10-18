package br.com.gestaodireta.financial.dto;

import java.time.LocalDate;

public record FinancialCommitmentCurrentResponse(
        LocalDate referenceDate,
        FinancialCommitmentAmountResponse overduePayables,
        FinancialCommitmentAmountResponse overdueReceivables) {}
