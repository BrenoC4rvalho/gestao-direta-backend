package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.financial.repository.HarvestSeasonFinancialTotalsProjection;
import br.com.gestaodireta.harvest.dto.FinancialAmountSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestComparisonSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestOpenAmountsSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestPlanningComparisonResponse;
import br.com.gestaodireta.harvest.dto.HarvestPlanningSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestProjectionSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestRealizedSummaryResponse;
import br.com.gestaodireta.harvest.dto.PlanningComparisonMetricResponse;
import br.com.gestaodireta.harvest.enumeration.ComparisonSemantic;
import br.com.gestaodireta.harvest.enumeration.CostVarianceStatus;
import br.com.gestaodireta.harvest.enumeration.PlanningComparisonBasis;
import br.com.gestaodireta.harvest.enumeration.PlanningComparisonDifferenceUnit;
import br.com.gestaodireta.harvest.enumeration.PlanningComparisonPosition;
import br.com.gestaodireta.harvest.enumeration.PlanningComparisonState;
import br.com.gestaodireta.harvest.enumeration.ProfitPerformanceStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class HarvestFinancialSummaryCalculator {

    public HarvestPlanningSummaryResponse planning(
            BigDecimal plannedCost, BigDecimal plannedRevenue) {
        BigDecimal resolvedCost = zeroIfNull(plannedCost);
        BigDecimal resolvedRevenue = zeroIfNull(plannedRevenue);
        BigDecimal profit = resolvedRevenue.subtract(resolvedCost);

        return new HarvestPlanningSummaryResponse(
                resolvedCost, resolvedRevenue, profit, margin(profit, resolvedRevenue));
    }

    public HarvestRealizedSummaryResponse realized(HarvestSeasonFinancialTotalsProjection totals) {
        return realized(totals.getRealizedCost(), totals.getRealizedRevenue());
    }

    public HarvestRealizedSummaryResponse realized(
            BigDecimal realizedCost, BigDecimal realizedRevenue) {
        BigDecimal resolvedCost = zeroIfNull(realizedCost);
        BigDecimal resolvedRevenue = zeroIfNull(realizedRevenue);
        BigDecimal profit = resolvedRevenue.subtract(resolvedCost);

        return new HarvestRealizedSummaryResponse(
                resolvedCost, resolvedRevenue, profit, margin(profit, resolvedRevenue));
    }

    public HarvestProjectionSummaryResponse projection(
            HarvestRealizedSummaryResponse realized,
            HarvestSeasonFinancialTotalsProjection totals) {
        return projection(realized, totals.getOpenCost(), totals.getOpenRevenue());
    }

    public HarvestProjectionSummaryResponse projection(
            HarvestRealizedSummaryResponse realized, BigDecimal openCost, BigDecimal openRevenue) {
        BigDecimal projectedCost = realized.realizedCost().add(zeroIfNull(openCost));
        BigDecimal projectedRevenue = realized.realizedRevenue().add(zeroIfNull(openRevenue));
        BigDecimal profit = projectedRevenue.subtract(projectedCost);

        return new HarvestProjectionSummaryResponse(
                projectedCost, projectedRevenue, profit, margin(profit, projectedRevenue));
    }

    public HarvestComparisonSummaryResponse comparison(
            HarvestPlanningSummaryResponse planning, HarvestProjectionSummaryResponse projection) {
        BigDecimal profitPerformanceAmount =
                projection.projectedProfit().subtract(planning.plannedProfit());
        BigDecimal costVarianceAmount = projection.projectedCost().subtract(planning.plannedCost());

        return new HarvestComparisonSummaryResponse(
                profitPerformanceAmount,
                percentage(profitPerformanceAmount, planning.plannedProfit().abs()),
                profitPerformanceStatus(projection.projectedProfit(), planning.plannedProfit()),
                costVarianceAmount,
                percentage(costVarianceAmount, planning.plannedCost()),
                costVarianceStatus(costVarianceAmount, planning.plannedCost()));
    }

    public HarvestOpenAmountsSummaryResponse openAmounts(
            HarvestSeasonFinancialTotalsProjection totals) {
        return new HarvestOpenAmountsSummaryResponse(
                zeroIfNull(totals.getOpenCost()),
                zeroIfNull(totals.getOpenRevenue()),
                new FinancialAmountSummaryResponse(
                        zeroIfNull(totals.getPendingPayableAmount()),
                        zeroIfNull(totals.getPendingReceivableAmount())),
                new FinancialAmountSummaryResponse(
                        zeroIfNull(totals.getOverduePayableAmount()),
                        zeroIfNull(totals.getOverdueReceivableAmount())));
    }

    public HarvestPlanningComparisonResponse planningComparison(
            boolean hasPlanning,
            boolean hasCurrentData,
            boolean isPlanned,
            boolean useProjection,
            HarvestPlanningSummaryResponse planning,
            HarvestRealizedSummaryResponse realized,
            HarvestProjectionSummaryResponse projection) {
        if (!hasPlanning) {
            return emptyPlanningComparison(PlanningComparisonState.MISSING_PLANNING);
        }
        if (isPlanned) {
            return emptyPlanningComparison(PlanningComparisonState.PLANNED);
        }
        if (!hasCurrentData) {
            return emptyPlanningComparison(PlanningComparisonState.MISSING_CURRENT_DATA);
        }
        if (useProjection) {
            return comparison(
                    PlanningComparisonBasis.PROJECTED,
                    planning,
                    projection.projectedCost(),
                    projection.projectedRevenue(),
                    projection.projectedProfit(),
                    projection.projectedMargin());
        }
        return comparison(
                PlanningComparisonBasis.REALIZED,
                planning,
                realized.realizedCost(),
                realized.realizedRevenue(),
                realized.realizedProfit(),
                realized.realizedMargin());
    }

    private HarvestPlanningComparisonResponse emptyPlanningComparison(
            PlanningComparisonState state) {
        return new HarvestPlanningComparisonResponse(state, null, null, null, null, null);
    }

    private HarvestPlanningComparisonResponse comparison(
            PlanningComparisonBasis basis,
            HarvestPlanningSummaryResponse planning,
            BigDecimal currentCost,
            BigDecimal currentRevenue,
            BigDecimal currentProfit,
            BigDecimal currentMargin) {
        return new HarvestPlanningComparisonResponse(
                PlanningComparisonState.READY,
                basis,
                comparisonMetric(
                        planning.plannedCost(),
                        currentCost,
                        true,
                        PlanningComparisonDifferenceUnit.AMOUNT),
                comparisonMetric(
                        planning.plannedRevenue(),
                        currentRevenue,
                        false,
                        PlanningComparisonDifferenceUnit.AMOUNT),
                comparisonMetric(
                        planning.plannedProfit(),
                        currentProfit,
                        false,
                        PlanningComparisonDifferenceUnit.AMOUNT),
                comparisonMetric(
                        planning.plannedMargin(),
                        currentMargin,
                        false,
                        PlanningComparisonDifferenceUnit.PERCENTAGE_POINTS));
    }

    public PlanningComparisonMetricResponse comparisonMetric(
            BigDecimal planned,
            BigDecimal current,
            boolean lowerIsBetter,
            PlanningComparisonDifferenceUnit differenceUnit) {
        BigDecimal difference = current.subtract(planned);
        int comparison = difference.compareTo(BigDecimal.ZERO);
        return new PlanningComparisonMetricResponse(
                planned,
                current,
                difference,
                differenceUnit == PlanningComparisonDifferenceUnit.AMOUNT
                        ? percentage(difference, planned.abs())
                        : null,
                position(comparison),
                semantic(comparison, lowerIsBetter),
                differenceUnit);
    }

    private PlanningComparisonPosition position(int comparison) {
        if (comparison > 0) {
            return PlanningComparisonPosition.ABOVE_PLANNED;
        }
        if (comparison < 0) {
            return PlanningComparisonPosition.BELOW_PLANNED;
        }
        return PlanningComparisonPosition.ON_TARGET;
    }

    private ComparisonSemantic semantic(int comparison, boolean lowerIsBetter) {
        if (comparison == 0) {
            return ComparisonSemantic.NEUTRAL;
        }
        if ((comparison < 0 && lowerIsBetter) || (comparison > 0 && !lowerIsBetter)) {
            return ComparisonSemantic.BETTER;
        }
        return ComparisonSemantic.WORSE;
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal base) {
        if (BigDecimal.ZERO.compareTo(base) == 0) {
            return null;
        }

        return value.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal margin(BigDecimal profit, BigDecimal revenue) {
        if (BigDecimal.ZERO.compareTo(revenue) == 0) {
            return BigDecimal.ZERO;
        }

        return profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);
    }

    private ProfitPerformanceStatus profitPerformanceStatus(
            BigDecimal projectedProfit, BigDecimal plannedProfit) {
        if (BigDecimal.ZERO.compareTo(plannedProfit) == 0) {
            return ProfitPerformanceStatus.NOT_APPLICABLE;
        }

        int comparison = projectedProfit.compareTo(plannedProfit);
        if (comparison > 0) {
            return ProfitPerformanceStatus.ABOVE_PLANNED;
        }
        if (comparison < 0) {
            return ProfitPerformanceStatus.BELOW_PLANNED;
        }
        return ProfitPerformanceStatus.ON_TARGET;
    }

    private CostVarianceStatus costVarianceStatus(
            BigDecimal costVarianceAmount, BigDecimal plannedCost) {
        if (BigDecimal.ZERO.compareTo(plannedCost) == 0) {
            return CostVarianceStatus.NOT_APPLICABLE;
        }

        int comparison = costVarianceAmount.compareTo(BigDecimal.ZERO);
        if (comparison > 0) {
            return CostVarianceStatus.ABOVE_PLANNED;
        }
        if (comparison < 0) {
            return CostVarianceStatus.BELOW_PLANNED;
        }
        return CostVarianceStatus.ON_TARGET;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
