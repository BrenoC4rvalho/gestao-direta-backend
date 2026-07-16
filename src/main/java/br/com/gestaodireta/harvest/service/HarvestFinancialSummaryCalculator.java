package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.financial.repository.HarvestSeasonFinancialTotalsProjection;
import br.com.gestaodireta.harvest.dto.FinancialAmountSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestComparisonSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestOpenAmountsSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestPlanningSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestProjectionSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestRealizedSummaryResponse;
import br.com.gestaodireta.harvest.enumeration.CostVarianceStatus;
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

        return new HarvestPlanningSummaryResponse(
                resolvedCost, resolvedRevenue, resolvedRevenue.subtract(resolvedCost));
    }

    public HarvestRealizedSummaryResponse realized(HarvestSeasonFinancialTotalsProjection totals) {
        BigDecimal realizedCost = zeroIfNull(totals.getRealizedCost());
        BigDecimal realizedRevenue = zeroIfNull(totals.getRealizedRevenue());

        return new HarvestRealizedSummaryResponse(
                realizedCost, realizedRevenue, realizedRevenue.subtract(realizedCost));
    }

    public HarvestProjectionSummaryResponse projection(
            HarvestRealizedSummaryResponse realized,
            HarvestSeasonFinancialTotalsProjection totals) {
        BigDecimal projectedCost = realized.realizedCost().add(zeroIfNull(totals.getOpenCost()));
        BigDecimal projectedRevenue =
                realized.realizedRevenue().add(zeroIfNull(totals.getOpenRevenue()));

        return new HarvestProjectionSummaryResponse(
                projectedCost, projectedRevenue, projectedRevenue.subtract(projectedCost));
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
                new FinancialAmountSummaryResponse(
                        zeroIfNull(totals.getPendingPayableAmount()),
                        zeroIfNull(totals.getPendingReceivableAmount())),
                new FinancialAmountSummaryResponse(
                        zeroIfNull(totals.getOverduePayableAmount()),
                        zeroIfNull(totals.getOverdueReceivableAmount())));
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal base) {
        if (BigDecimal.ZERO.compareTo(base) == 0) {
            return null;
        }

        return value.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
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
