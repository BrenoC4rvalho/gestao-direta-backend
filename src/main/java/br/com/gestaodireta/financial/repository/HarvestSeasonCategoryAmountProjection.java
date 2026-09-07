package br.com.gestaodireta.financial.repository;

import java.math.BigDecimal;

public interface HarvestSeasonCategoryAmountProjection {

    Long getCategoryId();

    String getCategoryName();

    BigDecimal getAmount();
}
