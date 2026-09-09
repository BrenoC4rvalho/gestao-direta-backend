package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface FinancialSummaryProjection {

    BigDecimal getPaidIncome();

    BigDecimal getPaidExpense();

    BigDecimal getExpectedIncome();

    BigDecimal getExpectedExpense();

    BigDecimal getPayableInHorizon();

    BigDecimal getOverdueExpenses();

    BigDecimal getReceivableInHorizon();
}
