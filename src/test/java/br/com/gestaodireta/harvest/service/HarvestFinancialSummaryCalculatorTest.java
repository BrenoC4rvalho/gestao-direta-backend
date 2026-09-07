package br.com.gestaodireta.harvest.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.harvest.dto.HarvestPlanningSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestRealizedSummaryResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HarvestFinancialSummaryCalculatorTest {

    private final HarvestFinancialSummaryCalculator calculator =
            new HarvestFinancialSummaryCalculator();

    @Test
    void shouldReturnZeroMarginsWhenRevenueIsZero() {
        HarvestPlanningSummaryResponse planning =
                calculator.planning(decimal("100"), BigDecimal.ZERO);
        HarvestRealizedSummaryResponse realized =
                calculator.realized(decimal("40"), BigDecimal.ZERO);
        var projection = calculator.projection(realized, decimal("20"), BigDecimal.ZERO);

        assertThat(planning.plannedMargin()).isEqualByComparingTo("0.00");
        assertThat(realized.realizedMargin()).isEqualByComparingTo("0.00");
        assertThat(projection.projectedMargin()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldCalculateRealizedAmountsAndMargin() {
        HarvestRealizedSummaryResponse realized =
                calculator.realized(decimal("35"), decimal("100"));

        assertThat(realized.realizedCost()).isEqualByComparingTo("35.00");
        assertThat(realized.realizedRevenue()).isEqualByComparingTo("100.00");
        assertThat(realized.realizedProfit()).isEqualByComparingTo("65.00");
        assertThat(realized.realizedMargin()).isEqualByComparingTo("65.00");
    }

    @Test
    void shouldCalculateProjectionWithOnlyRealizedAmounts() {
        var projection =
                calculator.projection(
                        calculator.realized(decimal("40"), decimal("100")), null, null);

        assertThat(projection.projectedCost()).isEqualByComparingTo("40.00");
        assertThat(projection.projectedRevenue()).isEqualByComparingTo("100.00");
        assertThat(projection.projectedProfit()).isEqualByComparingTo("60.00");
    }

    @Test
    void shouldCalculateProjectionWithOnlyOpenAmounts() {
        var projection =
                calculator.projection(
                        calculator.realized(null, null), decimal("40"), decimal("100"));

        assertThat(projection.projectedCost()).isEqualByComparingTo("40.00");
        assertThat(projection.projectedRevenue()).isEqualByComparingTo("100.00");
        assertThat(projection.projectedProfit()).isEqualByComparingTo("60.00");
        assertThat(projection.projectedMargin()).isEqualByComparingTo("60.00");
    }

    @Test
    void shouldCalculateProjectionWithRealizedAndOpenAmounts() {
        var projection =
                calculator.projection(
                        calculator.realized(decimal("40"), decimal("100")),
                        decimal("10"),
                        decimal("50"));

        assertThat(projection.projectedCost()).isEqualByComparingTo("50.00");
        assertThat(projection.projectedRevenue()).isEqualByComparingTo("150.00");
        assertThat(projection.projectedProfit()).isEqualByComparingTo("100.00");
        assertThat(projection.projectedMargin()).isEqualByComparingTo("66.67");
    }

    @Test
    void shouldCalculateCostVarianceAboveBelowAndOnTarget() {
        HarvestPlanningSummaryResponse planning =
                calculator.planning(decimal("100"), decimal("200"));

        assertThat(
                        calculator
                                .comparison(
                                        planning,
                                        calculator.projection(
                                                calculator.realized(decimal("120"), decimal("200")),
                                                null,
                                                null))
                                .costVarianceAmount())
                .isEqualByComparingTo("20.00");
        assertThat(
                        calculator
                                .comparison(
                                        planning,
                                        calculator.projection(
                                                calculator.realized(decimal("80"), decimal("200")),
                                                null,
                                                null))
                                .costVarianceAmount())
                .isEqualByComparingTo("-20.00");
        assertThat(
                        calculator
                                .comparison(
                                        planning,
                                        calculator.projection(
                                                calculator.realized(decimal("100"), decimal("200")),
                                                null,
                                                null))
                                .costVarianceAmount())
                .isEqualByComparingTo("0.00");
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
