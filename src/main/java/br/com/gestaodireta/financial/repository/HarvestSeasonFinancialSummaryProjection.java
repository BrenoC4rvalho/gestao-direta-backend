package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface HarvestSeasonFinancialSummaryProjection {

    BigDecimal getRealizedCost();

    BigDecimal getRealizedRevenue();

    BigDecimal getPendingExpenses();

    BigDecimal getOverdueExpenses();

    BigDecimal getPendingRevenue();

    Long getTransactionCount();

    Long getIncomeCount();

    Long getExpenseCount();
}
