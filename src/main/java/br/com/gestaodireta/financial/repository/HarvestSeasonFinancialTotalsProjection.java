package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface HarvestSeasonFinancialTotalsProjection {

    BigDecimal getRealizedCost();

    BigDecimal getRealizedRevenue();

    BigDecimal getOpenCost();

    BigDecimal getOpenRevenue();

    BigDecimal getPendingPayableAmount();

    BigDecimal getPendingReceivableAmount();

    BigDecimal getOverduePayableAmount();

    BigDecimal getOverdueReceivableAmount();

    Long getTransactionCount();

    Long getIncomeCount();

    Long getExpenseCount();
}
