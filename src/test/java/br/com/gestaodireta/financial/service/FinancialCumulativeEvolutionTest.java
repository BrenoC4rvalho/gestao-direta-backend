package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.financial.dto.FinancialCumulativeEvolutionPointResponse;
import br.com.gestaodireta.financial.dto.FinancialEvolutionPointResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialCumulativeEvolutionTest {

    @Test
    void shouldAccumulateRealizedIncomeAndExpenseAndCarryEmptyPeriods() {
        List<FinancialEvolutionPointResponse> evolution =
                List.of(
                        point("2026-01", "Jan", "100.00", "60.00"),
                        point("2026-02", "Fev", "50.00", "20.00"),
                        point("2026-03", "Mar", "25.00", "40.00"),
                        point("2026-04", "Abr", "0.00", "0.00"));

        List<FinancialCumulativeEvolutionPointResponse> cumulative =
                FinancialReportService.realizedCumulativeEvolution(evolution);

        assertThat(cumulative)
                .extracting(FinancialCumulativeEvolutionPointResponse::cumulativeIncome)
                .containsExactly(
                        new BigDecimal("100.00"),
                        new BigDecimal("150.00"),
                        new BigDecimal("175.00"),
                        new BigDecimal("175.00"));
        assertThat(cumulative)
                .extracting(FinancialCumulativeEvolutionPointResponse::cumulativeExpense)
                .containsExactly(
                        new BigDecimal("60.00"),
                        new BigDecimal("80.00"),
                        new BigDecimal("120.00"),
                        new BigDecimal("120.00"));
    }

    private FinancialEvolutionPointResponse point(
            String period, String label, String realizedIncome, String realizedExpense) {
        LocalDate periodStart = LocalDate.parse(period + "-01");
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        BigDecimal income = new BigDecimal(realizedIncome);
        BigDecimal expense = new BigDecimal(realizedExpense);

        return new FinancialEvolutionPointResponse(
                period,
                label,
                periodStart,
                periodEnd,
                income,
                expense,
                income.subtract(expense),
                0L,
                income,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                expense,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                income.subtract(expense),
                false);
    }
}
