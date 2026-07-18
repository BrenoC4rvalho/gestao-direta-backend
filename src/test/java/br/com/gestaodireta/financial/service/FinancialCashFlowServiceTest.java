package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.financial.dto.CashFlowResponse;
import br.com.gestaodireta.financial.repository.CashFlowAmountProjection;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.financial.repository.MonthlyCashFlowProjection;
import br.com.gestaodireta.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FinancialCashFlowServiceTest {

    private final FinancialTransactionRepository transactionRepository =
            Mockito.mock(FinancialTransactionRepository.class);
    private final FarmRepository farmRepository = Mockito.mock(FarmRepository.class);
    private final FinancialSummaryService service =
            new FinancialSummaryService(
                    transactionRepository,
                    farmRepository,
                    Clock.fixed(
                            Instant.parse("2026-06-29T03:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    void shouldBuildTwelveProjectedCashFlowPoints() {
        when(farmRepository.existsById(8L)).thenReturn(true);
        when(transactionRepository.summarizeCashFlowOpeningBalance(Mockito.eq(8L), Mockito.any()))
                .thenReturn(amount("20000", "0"));
        when(transactionRepository.summarizeMonthlyCashFlow(
                        Mockito.eq(8L), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(month(1, "30000", "5000"), month(2, "15000", "8000")));
        when(transactionRepository.summarizeOpenCashFlowBeforeYear(Mockito.eq(8L), Mockito.any()))
                .thenReturn(amount("0", "0"));

        CashFlowResponse response = service.getCashFlow(8L, 2026);

        assertThat(response.openingBalance()).isEqualByComparingTo("20000");
        assertThat(response.points()).hasSize(12);
        assertThat(response.points().get(0).label()).isEqualTo("Jan");
        assertThat(response.points().get(0).netFlow()).isEqualByComparingTo("25000");
        assertThat(response.points().get(0).balance()).isEqualByComparingTo("45000");
        assertThat(response.points().get(1).balance()).isEqualByComparingTo("52000");
        assertThat(response.points().get(2).income()).isEqualByComparingTo("0");
        assertThat(response.points().get(2).balance()).isEqualByComparingTo("52000");
        assertThat(response.closingBalance()).isEqualByComparingTo("52000");
    }

    @Test
    void shouldUseCurrentYearAndAddPriorOpenAmountsToJanuary() {
        when(farmRepository.existsById(8L)).thenReturn(true);
        when(transactionRepository.summarizeCashFlowOpeningBalance(Mockito.eq(8L), Mockito.any()))
                .thenReturn(amount("100", "20"));
        when(transactionRepository.summarizeMonthlyCashFlow(
                        Mockito.eq(8L), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());
        when(transactionRepository.summarizeOpenCashFlowBeforeYear(Mockito.eq(8L), Mockito.any()))
                .thenReturn(amount("5", "30"));

        CashFlowResponse response = service.getCashFlow(8L, null);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.points().get(0).income()).isEqualByComparingTo("5");
        assertThat(response.points().get(0).expense()).isEqualByComparingTo("30");
        assertThat(response.points().get(0).balance()).isEqualByComparingTo("55");
    }

    @Test
    void shouldRejectInvalidYear() {
        when(farmRepository.existsById(8L)).thenReturn(true);

        assertThatThrownBy(() -> service.getCashFlow(8L, 1999))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Year must be between 2000 and 2100");
    }

    private CashFlowAmountProjection amount(String income, String expense) {
        return new CashFlowAmountProjection() {
            @Override
            public BigDecimal getIncome() {
                return new BigDecimal(income);
            }

            @Override
            public BigDecimal getExpense() {
                return new BigDecimal(expense);
            }
        };
    }

    private MonthlyCashFlowProjection month(int month, String income, String expense) {
        return new MonthlyCashFlowProjection() {
            @Override
            public Integer getMonth() {
                return month;
            }

            @Override
            public BigDecimal getIncome() {
                return new BigDecimal(income);
            }

            @Override
            public BigDecimal getExpense() {
                return new BigDecimal(expense);
            }
        };
    }
}
