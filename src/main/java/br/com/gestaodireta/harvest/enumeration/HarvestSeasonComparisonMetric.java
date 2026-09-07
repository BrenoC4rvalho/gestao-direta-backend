package br.com.gestaodireta.harvest.enumeration;

public enum HarvestSeasonComparisonMetric {
    AREA_HECTARES(HarvestSeasonComparisonDirection.NO_COMPARISON),
    PLANNED_COST(HarvestSeasonComparisonDirection.NO_COMPARISON),
    PLANNED_REVENUE(HarvestSeasonComparisonDirection.NO_COMPARISON),
    PLANNED_RESULT(HarvestSeasonComparisonDirection.NO_COMPARISON),
    PLANNED_MARGIN(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER),
    PROJECTED_COST(HarvestSeasonComparisonDirection.NO_COMPARISON),
    PROJECTED_REVENUE(HarvestSeasonComparisonDirection.NO_COMPARISON),
    PROJECTED_PROFIT(HarvestSeasonComparisonDirection.NO_COMPARISON),
    PROJECTED_MARGIN(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER),
    REALIZED_COST(HarvestSeasonComparisonDirection.NO_COMPARISON),
    REALIZED_REVENUE(HarvestSeasonComparisonDirection.NO_COMPARISON),
    REALIZED_PROFIT(HarvestSeasonComparisonDirection.NO_COMPARISON),
    REALIZED_MARGIN(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER),
    PLANNED_COST_PER_HECTARE(HarvestSeasonComparisonDirection.LOWER_IS_BETTER),
    PLANNED_REVENUE_PER_HECTARE(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER),
    PLANNED_RESULT_PER_HECTARE(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER),
    PROJECTED_COST_PER_HECTARE(HarvestSeasonComparisonDirection.LOWER_IS_BETTER),
    PROJECTED_REVENUE_PER_HECTARE(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER),
    PROJECTED_PROFIT_PER_HECTARE(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER),
    REALIZED_COST_PER_HECTARE(HarvestSeasonComparisonDirection.LOWER_IS_BETTER),
    REALIZED_REVENUE_PER_HECTARE(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER),
    REALIZED_PROFIT_PER_HECTARE(HarvestSeasonComparisonDirection.HIGHER_IS_BETTER);

    private final HarvestSeasonComparisonDirection direction;

    HarvestSeasonComparisonMetric(HarvestSeasonComparisonDirection direction) {
        this.direction = direction;
    }

    public HarvestSeasonComparisonDirection direction() {
        return direction;
    }
}
