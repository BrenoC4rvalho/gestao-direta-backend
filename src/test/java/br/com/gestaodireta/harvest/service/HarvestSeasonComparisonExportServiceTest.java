package br.com.gestaodireta.harvest.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.harvest.dto.HarvestPlanningSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestProjectionSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestRealizedSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonBestResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonHarvestResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonPerHectareComparisonResponse;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonComparisonMetric;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class HarvestSeasonComparisonExportServiceTest {

    @Test
    void shouldGenerateNeutralComparisonPdfWithoutBestIndicators() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC);
        HarvestSeasonComparisonResponse comparison = comparison();
        HarvestSeasonService harvestSeasonService =
                new HarvestSeasonService(null, null, null, null, null, null, null, clock) {
                    @Override
                    public HarvestSeasonComparisonResponse compare(
                            Long farmId, Long harvestSeasonIdA, Long harvestSeasonIdB) {
                        return comparison;
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
        HarvestSeasonComparisonExportService service =
                new HarvestSeasonComparisonExportService(harvestSeasonService, farmService, clock);

        var file = service.exportPdf(1L, 1L, 2L);

        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.filename()).isEqualTo("comparativo-safras-2026-09-07.pdf");
        assertThat(file.content()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        PdfReader reader = new PdfReader(file.content());
        String text = pdfText(reader);
        reader.close();
        assertThat(text)
                .contains("COMPARATIVO DE SAFRAS")
                .contains("Fazenda Boa Safra")
                .contains("Planejamento")
                .contains("Realizado")
                .contains("Indicadores por hectare")
                .doesNotContain("Melhor")
                .doesNotContain("BEST")
                .doesNotContain("Troféu");
    }

    private String pdfText(PdfReader reader) throws Exception {
        StringBuilder text = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page));
        }
        return text.toString();
    }

    private HarvestSeasonComparisonResponse comparison() {
        return new HarvestSeasonComparisonResponse(
                harvest(1L, "Safra 2025/2026", new BigDecimal("100.00")),
                harvest(2L, "Safra 2026/2027", new BigDecimal("120.00")),
                List.of(),
                List.of(),
                List.of(
                        new HarvestSeasonComparisonBestResponse(
                                HarvestSeasonComparisonMetric.REALIZED_REVENUE, List.of(2L))));
    }

    private HarvestSeasonComparisonHarvestResponse harvest(
            Long id, String name, BigDecimal realizedRevenue) {
        return new HarvestSeasonComparisonHarvestResponse(
                id,
                name,
                HarvestSeasonStatus.FINISHED,
                "Café",
                LocalDate.of(2025, 10, 1),
                LocalDate.of(2026, 9, 30),
                new BigDecimal("10.00"),
                new HarvestPlanningSummaryResponse(
                        new BigDecimal("100.00"),
                        new BigDecimal("200.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("50.00")),
                new HarvestProjectionSummaryResponse(
                        new BigDecimal("110.00"),
                        new BigDecimal("220.00"),
                        new BigDecimal("110.00"),
                        new BigDecimal("50.00")),
                new HarvestRealizedSummaryResponse(
                        new BigDecimal("90.00"),
                        realizedRevenue,
                        realizedRevenue.subtract(new BigDecimal("90.00")),
                        new BigDecimal("55.00")),
                new HarvestSeasonPerHectareComparisonResponse(
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("11.00"),
                        new BigDecimal("22.00"),
                        new BigDecimal("11.00"),
                        new BigDecimal("9.00"),
                        realizedRevenue.divide(new BigDecimal("10.00")),
                        realizedRevenue
                                .subtract(new BigDecimal("90.00"))
                                .divide(new BigDecimal("10.00"))));
    }
}
