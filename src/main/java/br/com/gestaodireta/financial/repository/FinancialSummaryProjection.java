package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface FinancialSummaryProjection {

    BigDecimal getPaidIncome();

    BigDecimal getPaidExpense();

    BigDecimal getExpectedIncome();

    BigDecimal getExpectedExpense();

    BigDecimal getPayableNext30Days();

    BigDecimal getOverdueExpenses();

    BigDecimal getOverdueIncome();

    BigDecimal getReceivablePendingNext30Days();
}
