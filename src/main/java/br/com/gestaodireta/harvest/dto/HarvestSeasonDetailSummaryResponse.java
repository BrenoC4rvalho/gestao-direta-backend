package br.com.gestaodireta.harvest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record HarvestSeasonDetailSummaryResponse(
        Long harvestSeasonId,
        String harvestSeasonName,
        Long productionActivityId,
        String productionActivityName,
        Long farmId,
        String farmName,
        BigDecimal areaHectares,
        HarvestPlanningSummaryResponse planning,
        HarvestRealizedSummaryResponse realized,
        HarvestProjectionSummaryResponse projection,
        HarvestComparisonSummaryResponse comparison,
        HarvestOpenAmountsSummaryResponse openAmounts,
        Long transactionCount,
        Long incomeCount,
        Long expenseCount) {

    @JsonIgnore
    public BigDecimal expectedCost() {
        return planning.plannedCost();
    }

    @JsonIgnore
    public BigDecimal expectedRevenue() {
        return planning.plannedRevenue();
    }

    @JsonIgnore
    public BigDecimal expectedProfit() {
        return planning.plannedProfit();
    }

    @JsonIgnore
    public BigDecimal realizedCost() {
        return realized.realizedCost();
    }

    @JsonIgnore
    public BigDecimal realizedRevenue() {
        return realized.realizedRevenue();
    }

    @JsonIgnore
    public BigDecimal realizedProfit() {
        return realized.realizedProfit();
    }

    @JsonIgnore
    public BigDecimal pendingExpenses() {
        return openAmounts.pending().payableAmount();
    }

    @JsonIgnore
    public BigDecimal overdueExpenses() {
        return openAmounts.overdue().payableAmount();
    }

    @JsonIgnore
    public BigDecimal pendingRevenue() {
        return openAmounts.pending().receivableAmount();
    }

    @JsonIgnore
    public BigDecimal costPerHectare() {
        return perHectare(realizedCost());
    }

    @JsonIgnore
    public BigDecimal revenuePerHectare() {
        return perHectare(realizedRevenue());
    }

    @JsonIgnore
    public BigDecimal profitPerHectare() {
        return perHectare(realizedProfit());
    }

    private BigDecimal perHectare(BigDecimal amount) {
        if (areaHectares == null || BigDecimal.ZERO.compareTo(areaHectares) == 0) {
            return null;
        }

        return amount.divide(areaHectares, 2, RoundingMode.HALF_UP);
    }
}
