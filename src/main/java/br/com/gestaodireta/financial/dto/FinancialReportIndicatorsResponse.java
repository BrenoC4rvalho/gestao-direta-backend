package br.com.gestaodireta.financial.dto;

public record FinancialReportIndicatorsResponse(
        long analyzedMonthCount,
        FinancialReportPeriodIndicatorResponse highestIncomePeriod,
        FinancialReportPeriodIndicatorResponse highestExpensePeriod,
        FinancialReportPeriodIndicatorResponse bestBalancePeriod,
        FinancialReportPeriodIndicatorResponse criticalPeriod,
        FinancialReportCategoryIndicatorResponse highestExpenseCategory,
        FinancialReportHarvestIndicatorResponse mostProfitableHarvest) {}
