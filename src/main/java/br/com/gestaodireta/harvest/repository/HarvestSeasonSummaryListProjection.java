package br.com.gestaodireta.harvest.repository;

import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface HarvestSeasonSummaryListProjection {

    Long getId();

    Long getFarmId();

    String getFarmName();

    Long getProductionActivityId();

    String getProductionActivityName();

    String getName();

    String getDescription();

    LocalDate getStartDate();

    LocalDate getEndDate();

    BigDecimal getExpectedCost();

    BigDecimal getExpectedRevenue();

    BigDecimal getAreaHectares();

    HarvestSeasonStatus getStatus();

    BigDecimal getRealizedCost();

    BigDecimal getRealizedRevenue();

    BigDecimal getPendingExpenses();

    BigDecimal getOverdueExpenses();

    BigDecimal getPendingRevenue();

    BigDecimal getOverdueRevenue();

    Long getTransactionCount();

    Long getIncomeCount();

    Long getExpenseCount();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
