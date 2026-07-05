package br.com.gestaodireta.financial.dto;

import java.util.List;

public record FinancialAlertsResponse(
        Long farmId,
        List<OverdueBillAlertResponse> overdueBills,
        DueBillsSummaryResponse dueToday,
        DueBillsSummaryResponse dueNext7Days) {}
