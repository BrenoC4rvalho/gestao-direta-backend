package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface DueBillsSummaryProjection {

    Long getCount();

    BigDecimal getTotalAmount();
}
