package br.com.gestaodireta.financial.dto;

import java.math.BigDecimal;

public record FinancialReportPeriodIndicatorResponse(
        String period, String label, BigDecimal amount) {}
