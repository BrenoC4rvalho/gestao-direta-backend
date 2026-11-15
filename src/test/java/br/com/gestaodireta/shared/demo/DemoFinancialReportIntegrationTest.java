package br.com.gestaodireta.shared.demo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportResponse;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.service.FinancialReportService;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {
    "app.demo-seed.enabled=true",
    "app.demo-seed.user-email=admin@gestaodireta.com"
})
class DemoFinancialReportIntegrationTest extends PostgresIntegrationTest {

    @Autowired private FarmRepository farmRepository;
    @Autowired private FinancialReportService financialReportService;

    @Test
    void shouldExposeAllOctoberSegmentsInCashAndAccrualReports() {
        Long farmId = farmRepository.findByNameIgnoreCase("Fazenda Vale Verde")
                .orElseThrow()
                .getId();

        for (FinancialReportBasis basis : new FinancialReportBasis[] {
            FinancialReportBasis.CASH, FinancialReportBasis.ACCRUAL
        }) {
            FinancialReportResponse report = financialReportService.getReport(
                    new FinancialReportFilter(
                            farmId,
                            LocalDate.of(2026, 10, 1),
                            LocalDate.of(2026, 10, 31),
                            basis,
                            null,
                            null,
                            FinancialReportGranularity.MONTHLY));

            assertThat(report.summary().totalIncome()).isEqualByComparingTo("345000.00");
            assertThat(report.summary().totalExpense()).isEqualByComparingTo("185000.00");
            assertThat(report.summary().realizedIncome()).isEqualByComparingTo("180000.00");
            assertThat(report.summary().realizedExpense()).isEqualByComparingTo("95000.00");

            var point = report.evolution().getFirst();
            assertThat(point.realizedIncome()).isEqualByComparingTo("180000.00");
            assertThat(point.projectedIncome()).isEqualByComparingTo("120000.00");
            assertThat(point.overdueIncome()).isEqualByComparingTo("45000.00");
            assertThat(point.realizedExpense()).isEqualByComparingTo("95000.00");
            assertThat(point.projectedExpense()).isEqualByComparingTo("65000.00");
            assertThat(point.overdueExpense()).isEqualByComparingTo("25000.00");
        }
    }

    @TestConfiguration
    static class DemoClockConfiguration {

        @Bean
        @Primary
        Clock demoClock() {
            return Clock.fixed(
                    Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
