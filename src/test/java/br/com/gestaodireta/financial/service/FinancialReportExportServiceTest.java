package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialCashFlowResponse;
import br.com.gestaodireta.financial.dto.FinancialEvolutionPointResponse;
import br.com.gestaodireta.financial.dto.FinancialReportCommitmentsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportExportFile;
import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportResponse;
import br.com.gestaodireta.financial.dto.FinancialReportSummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialReportUnallocatedResponse;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class FinancialReportExportServiceTest {

    @Test
    void shouldGeneratePdfWithTheFinancialReportData() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T22:30:00Z"), ZoneOffset.UTC);
        FinancialReportResponse report = report();
        FinancialReportService reportService =
                new FinancialReportService(null, null, null, null, clock) {
                    @Override
                    public FinancialReportResponse getReport(FinancialReportFilter filter) {
                        return report;
                    }
                };
        Farm farm = new Farm();
        farm.setName("Fazenda Boa Safra");
        FarmService farmService =
                new FarmService(null, null, null, null) {
                    @Override
                    public Farm findEntityById(Long farmId) {
                        return farm;
                    }
                };
        FinancialReportExportService service =
                new FinancialReportExportService(reportService, farmService, null, null, clock);
        FinancialReportFilter filter =
                new FinancialReportFilter(
                        1L,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        FinancialReportBasis.CASH,
                        null,
                        null,
                        FinancialReportGranularity.MONTHLY);
        FinancialReportExportFile file = service.exportPdf(filter);

        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.filename()).isEqualTo("relatorio-financeiro-2026-01-01-a-2026-01-31.pdf");
        assertThat(file.content()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        assertThat(file.content()).hasSizeGreaterThan(500);
        PdfReader reader = new PdfReader(file.content());
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text)
                .contains("RELATÓRIO FINANCEIRO")
                .contains("Fazenda Boa Safra")
                .contains("Resumo executivo")
                .contains("R$");
    }

    private FinancialReportResponse report() {
        FinancialReportSummaryResponse summary =
                new FinancialReportSummaryResponse(
                        new BigDecimal("1000.00"),
                        new BigDecimal("250.00"),
                        new BigDecimal("750.00"),
                        new BigDecimal("75.00"),
                        new BigDecimal("600.00"),
                        new BigDecimal("200.00"),
                        new BigDecimal("400.00"),
                        new BigDecimal("50.00"));
        FinancialReportCommitmentsResponse commitments =
                new FinancialReportCommitmentsResponse(
                        new BigDecimal("400.00"),
                        new BigDecimal("50.00"),
                        BigDecimal.ZERO,
                        0L,
                        BigDecimal.ZERO,
                        0L,
                        false,
                        null,
                        null);
        FinancialEvolutionPointResponse evolution =
                new FinancialEvolutionPointResponse(
                        "2026-01",
                        "Jan/2026",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        new BigDecimal("1000.00"),
                        new BigDecimal("250.00"),
                        new BigDecimal("750.00"),
                        2L,
                        new BigDecimal("600.00"),
                        new BigDecimal("400.00"),
                        BigDecimal.ZERO,
                        0L,
                        new BigDecimal("200.00"),
                        new BigDecimal("50.00"),
                        BigDecimal.ZERO,
                        0L,
                        new BigDecimal("400.00"),
                        false);

        return new FinancialReportResponse(
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                FinancialReportBasis.CASH,
                summary,
                commitments,
                List.of(evolution),
                List.of(),
                new FinancialCashFlowResponse(BigDecimal.ZERO, BigDecimal.ZERO, List.of()),
                List.of(),
                List.of(),
                new FinancialReportIndicatorsResponse(1, null, null, null, null, null, null),
                new FinancialReportUnallocatedResponse(BigDecimal.ZERO, BigDecimal.ZERO, 0L));
    }
}
