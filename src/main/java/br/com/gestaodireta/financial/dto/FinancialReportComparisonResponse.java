package br.com.gestaodireta.financial.dto;

import java.time.LocalDate;

public record FinancialReportComparisonResponse(
        LocalDate currentStartDate,
        LocalDate currentEndDate,
        LocalDate previousStartDate,
        LocalDate previousEndDate,
        boolean previousDataAvailable,
        FinancialReportComparisonMetricResponse realizedIncome,
        FinancialReportComparisonMetricResponse realizedExpense,
        FinancialReportComparisonMetricResponse realizedResult) {}
