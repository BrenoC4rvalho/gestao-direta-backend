package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface CashFlowAmountProjection {

    BigDecimal getIncome();

    BigDecimal getExpense();
}
