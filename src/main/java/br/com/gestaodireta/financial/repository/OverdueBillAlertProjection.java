package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface OverdueBillAlertProjection {

    Long getTransactionId();

    String getDescription();

    String getCategoryName();

    BigDecimal getAmount();

    LocalDate getDueDate();
}
