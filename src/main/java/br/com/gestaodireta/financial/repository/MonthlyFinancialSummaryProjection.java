package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface MonthlyFinancialSummaryProjection {

    BigDecimal getIncome();

    BigDecimal getExpense();
}
