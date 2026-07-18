package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface MonthlyCashFlowProjection {

    Integer getMonth();

    BigDecimal getIncome();

    BigDecimal getExpense();
}
