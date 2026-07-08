package br.com.gestaodireta.financial.dto;

public record FinancialAgendaSummaryResponse(
        Long farmId,
        FinancialAgendaAmountSummary overdueReceivable,
        FinancialAgendaAmountSummary overduePayable,
        FinancialAgendaAmountSummary pendingReceivable,
        FinancialAgendaAmountSummary pendingPayable,
        FinancialAgendaAmountSummary openReceivable,
        FinancialAgendaAmountSummary openPayable) {}
