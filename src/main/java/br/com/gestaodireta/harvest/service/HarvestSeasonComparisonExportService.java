package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.harvest.dto.HarvestPlanningSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestProjectionSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestRealizedSummaryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonExportFile;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonHarvestResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonComparisonResponse;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonComparisonMetric;
import br.com.gestaodireta.shared.pdf.PdfReportStyle;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HarvestSeasonComparisonExportService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final HarvestSeasonService harvestSeasonService;
    private final FarmService farmService;
    private final Clock clock;

    public HarvestSeasonComparisonExportService(
            HarvestSeasonService harvestSeasonService, FarmService farmService, Clock clock) {
        this.harvestSeasonService = harvestSeasonService;
        this.farmService = farmService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HarvestSeasonComparisonExportFile exportPdf(
            Long farmId, Long harvestAId, Long harvestBId) {
        HarvestSeasonComparisonResponse comparison =
                harvestSeasonService.compare(farmId, harvestAId, harvestBId);

        return new HarvestSeasonComparisonExportFile(
                "comparativo-safras-" + LocalDate.now(clock) + ".pdf",
                PDF_CONTENT_TYPE,
                createPdf(comparison, farmService.findEntityById(farmId).getName()));
    }

    private byte[] createPdf(HarvestSeasonComparisonResponse comparison, String farmName) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 42);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);
            writer.setPageEvent(PdfReportStyle.footer("Comparativo de Safras"));
            document.addTitle("Comparativo de Safras");
            document.addAuthor("Gestão Direta");
            document.open();

            addHeader(document, comparison, farmName);
            addHarvestContext(document, comparison.harvestA());
            addHarvestContext(document, comparison.harvestB());
            addComparisonTables(document, comparison);
            document.close();

            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate harvest comparison PDF", exception);
        }
    }

    private void addHeader(
            Document document, HarvestSeasonComparisonResponse comparison, String farmName)
            throws Exception {
        Paragraph product = new Paragraph("GESTÃO DIRETA", titleFont(16));
        product.setSpacingAfter(3);
        document.add(product);

        Paragraph title = new Paragraph("COMPARATIVO DE SAFRAS", subtitleFont(12));
        title.setSpacingAfter(12);
        document.add(title);

        PdfPTable metadata = new PdfPTable(new float[] {1, 2});
        metadata.setWidthPercentage(100);
        metadata.setSpacingAfter(12);
        addMetadata(metadata, "Fazenda", farmName);
        addMetadata(
                metadata,
                "Safras",
                comparison.harvestA().name() + " e " + comparison.harvestB().name());
        addMetadata(metadata, "Gerado em", DATE_TIME_FORMATTER.format(LocalDateTime.now(clock)));
        document.add(metadata);
    }

    private void addHarvestContext(
            Document document, HarvestSeasonComparisonHarvestResponse harvest) throws Exception {
        addSectionTitle(document, harvest.name());

        PdfPTable context = new PdfPTable(new float[] {1, 2});
        context.setWidthPercentage(100);
        context.setSpacingAfter(10);
        addMetadata(context, "Status", statusLabel(harvest.status().name()));
        addMetadata(context, "Atividade", harvest.productionActivityName());
        addMetadata(context, "Período", formatPeriod(harvest.startDate(), harvest.endDate()));
        addMetadata(context, "Área", formatArea(harvest.areaHectares()));
        document.add(context);
    }

    private void addComparisonTables(Document document, HarvestSeasonComparisonResponse comparison)
            throws Exception {
        for (MetricGroup group : MetricGroup.values()) {
            addSectionTitle(document, group.label());
            PdfPTable table = comparisonTable(comparison);

            for (HarvestSeasonComparisonMetric metric : group.metrics()) {
                addCell(table, metricLabel(metric), Element.ALIGN_LEFT, bodyFont(8), null);
                addCell(
                        table,
                        formatMetricValue(comparison.harvestA(), metric),
                        Element.ALIGN_RIGHT,
                        bodyFont(8),
                        null);
                addCell(
                        table,
                        formatMetricValue(comparison.harvestB(), metric),
                        Element.ALIGN_RIGHT,
                        bodyFont(8),
                        null);
            }

            document.add(table);
        }
    }

    private PdfPTable comparisonTable(HarvestSeasonComparisonResponse comparison) {
        PdfPTable table = new PdfPTable(new float[] {2.3F, 1.6F, 1.6F});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSpacingAfter(12);
        addTableHeader(table, "Indicador");
        addTableHeader(table, comparison.harvestA().name());
        addTableHeader(table, comparison.harvestB().name());
        return table;
    }

    private void addSectionTitle(Document document, String title) throws Exception {
        Paragraph section = new Paragraph(title, subtitleFont(10));
        section.setSpacingBefore(4);
        section.setSpacingAfter(6);
        document.add(section);
    }

    private void addMetadata(PdfPTable table, String label, String value) {
        addCell(table, label, Element.ALIGN_LEFT, labelFont(), null);
        addCell(table, value, Element.ALIGN_LEFT, bodyFont(9), null);
    }

    private void addTableHeader(PdfPTable table, String value) {
        addCell(table, value, Element.ALIGN_CENTER, headerFont(), PdfReportStyle.PRIMARY_COLOR);
    }

    private void addCell(
            PdfPTable table,
            String value,
            int alignment,
            org.openpdf.text.Font font,
            java.awt.Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "—" : value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorderColor(PdfReportStyle.BORDER_COLOR);
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        table.addCell(cell);
    }

    private String formatMetricValue(
            HarvestSeasonComparisonHarvestResponse harvest, HarvestSeasonComparisonMetric metric) {
        BigDecimal value = metricValue(harvest, metric);
        if (value == null) {
            return "—";
        }
        if (metric.name().contains("MARGIN")) {
            return formatPercentage(value);
        }
        return formatCurrency(value);
    }

    private BigDecimal metricValue(
            HarvestSeasonComparisonHarvestResponse harvest, HarvestSeasonComparisonMetric metric) {
        return switch (metric) {
            case PLANNED_COST ->
                    valueOf(harvest.planning(), HarvestPlanningSummaryResponse::plannedCost);
            case PLANNED_REVENUE ->
                    valueOf(harvest.planning(), HarvestPlanningSummaryResponse::plannedRevenue);
            case PLANNED_RESULT ->
                    valueOf(harvest.planning(), HarvestPlanningSummaryResponse::plannedProfit);
            case PLANNED_MARGIN ->
                    valueOf(harvest.planning(), HarvestPlanningSummaryResponse::plannedMargin);
            case PROJECTED_COST ->
                    valueOf(harvest.projection(), HarvestProjectionSummaryResponse::projectedCost);
            case PROJECTED_REVENUE ->
                    valueOf(
                            harvest.projection(),
                            HarvestProjectionSummaryResponse::projectedRevenue);
            case PROJECTED_PROFIT ->
                    valueOf(
                            harvest.projection(),
                            HarvestProjectionSummaryResponse::projectedProfit);
            case PROJECTED_MARGIN ->
                    valueOf(
                            harvest.projection(),
                            HarvestProjectionSummaryResponse::projectedMargin);
            case REALIZED_COST ->
                    valueOf(harvest.realized(), HarvestRealizedSummaryResponse::realizedCost);
            case REALIZED_REVENUE ->
                    valueOf(harvest.realized(), HarvestRealizedSummaryResponse::realizedRevenue);
            case REALIZED_PROFIT ->
                    valueOf(harvest.realized(), HarvestRealizedSummaryResponse::realizedProfit);
            case REALIZED_MARGIN ->
                    valueOf(harvest.realized(), HarvestRealizedSummaryResponse::realizedMargin);
            case PLANNED_COST_PER_HECTARE -> harvest.perHectare().plannedCostPerHectare();
            case PLANNED_REVENUE_PER_HECTARE -> harvest.perHectare().plannedRevenuePerHectare();
            case PLANNED_RESULT_PER_HECTARE -> harvest.perHectare().plannedResultPerHectare();
            case PROJECTED_COST_PER_HECTARE -> harvest.perHectare().projectedCostPerHectare();
            case PROJECTED_REVENUE_PER_HECTARE -> harvest.perHectare().projectedRevenuePerHectare();
            case PROJECTED_PROFIT_PER_HECTARE -> harvest.perHectare().projectedProfitPerHectare();
            case REALIZED_COST_PER_HECTARE -> harvest.perHectare().realizedCostPerHectare();
            case REALIZED_REVENUE_PER_HECTARE -> harvest.perHectare().realizedRevenuePerHectare();
            case REALIZED_PROFIT_PER_HECTARE -> harvest.perHectare().realizedProfitPerHectare();
            case AREA_HECTARES -> null;
        };
    }

    private <T> BigDecimal valueOf(T source, java.util.function.Function<T, BigDecimal> extractor) {
        return source == null ? null : extractor.apply(source);
    }

    private String metricLabel(HarvestSeasonComparisonMetric metric) {
        return switch (metric) {
            case PLANNED_COST -> "Custo planejado";
            case PLANNED_REVENUE -> "Receita planejada";
            case PLANNED_RESULT -> "Resultado planejado";
            case PLANNED_MARGIN -> "Margem planejada";
            case PROJECTED_COST -> "Custo projetado";
            case PROJECTED_REVENUE -> "Receita projetada";
            case PROJECTED_PROFIT -> "Lucro projetado";
            case PROJECTED_MARGIN -> "Margem projetada";
            case REALIZED_COST -> "Custo realizado";
            case REALIZED_REVENUE -> "Receita realizada";
            case REALIZED_PROFIT -> "Lucro realizado";
            case REALIZED_MARGIN -> "Margem realizada";
            case PLANNED_COST_PER_HECTARE -> "Custo planejado / ha";
            case PLANNED_REVENUE_PER_HECTARE -> "Receita planejada / ha";
            case PLANNED_RESULT_PER_HECTARE -> "Resultado planejado / ha";
            case PROJECTED_COST_PER_HECTARE -> "Custo projetado / ha";
            case PROJECTED_REVENUE_PER_HECTARE -> "Receita projetada / ha";
            case PROJECTED_PROFIT_PER_HECTARE -> "Lucro projetado / ha";
            case REALIZED_COST_PER_HECTARE -> "Custo realizado / ha";
            case REALIZED_REVENUE_PER_HECTARE -> "Receita realizada / ha";
            case REALIZED_PROFIT_PER_HECTARE -> "Lucro realizado / ha";
            case AREA_HECTARES -> "Área";
        };
    }

    private String formatCurrency(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value);
    }

    private String formatPercentage(BigDecimal value) {
        return new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(new Locale("pt", "BR")))
                        .format(value)
                + "%";
    }

    private String formatPeriod(LocalDate startDate, LocalDate endDate) {
        return DATE_FORMATTER.format(startDate)
                + " a "
                + (endDate == null ? "Sem data final" : DATE_FORMATTER.format(endDate));
    }

    private String formatArea(BigDecimal areaHectares) {
        return areaHectares == null ? "Área não informada" : areaHectares + " ha";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "PLANNED" -> "Planejada";
            case "IN_PROGRESS" -> "Em andamento";
            case "FINISHED" -> "Finalizada";
            case "INACTIVE" -> "Inativa";
            default -> status;
        };
    }

    private org.openpdf.text.Font titleFont(int size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, PdfReportStyle.PRIMARY_COLOR);
    }

    private org.openpdf.text.Font subtitleFont(int size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, PdfReportStyle.PRIMARY_COLOR);
    }

    private org.openpdf.text.Font labelFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PdfReportStyle.MUTED_COLOR);
    }

    private org.openpdf.text.Font headerFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, java.awt.Color.WHITE);
    }

    private org.openpdf.text.Font bodyFont(int size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, java.awt.Color.BLACK);
    }

    private enum MetricGroup {
        PLANNING(
                "Planejamento",
                List.of(
                        HarvestSeasonComparisonMetric.PLANNED_COST,
                        HarvestSeasonComparisonMetric.PLANNED_REVENUE,
                        HarvestSeasonComparisonMetric.PLANNED_RESULT,
                        HarvestSeasonComparisonMetric.PLANNED_MARGIN)),
        PROJECTION(
                "Projeção",
                List.of(
                        HarvestSeasonComparisonMetric.PROJECTED_COST,
                        HarvestSeasonComparisonMetric.PROJECTED_REVENUE,
                        HarvestSeasonComparisonMetric.PROJECTED_PROFIT,
                        HarvestSeasonComparisonMetric.PROJECTED_MARGIN)),
        REALIZED(
                "Realizado",
                List.of(
                        HarvestSeasonComparisonMetric.REALIZED_COST,
                        HarvestSeasonComparisonMetric.REALIZED_REVENUE,
                        HarvestSeasonComparisonMetric.REALIZED_PROFIT,
                        HarvestSeasonComparisonMetric.REALIZED_MARGIN)),
        PER_HECTARE(
                "Indicadores por hectare",
                List.of(
                        HarvestSeasonComparisonMetric.PLANNED_COST_PER_HECTARE,
                        HarvestSeasonComparisonMetric.PLANNED_REVENUE_PER_HECTARE,
                        HarvestSeasonComparisonMetric.PLANNED_RESULT_PER_HECTARE,
                        HarvestSeasonComparisonMetric.PROJECTED_COST_PER_HECTARE,
                        HarvestSeasonComparisonMetric.PROJECTED_REVENUE_PER_HECTARE,
                        HarvestSeasonComparisonMetric.PROJECTED_PROFIT_PER_HECTARE,
                        HarvestSeasonComparisonMetric.REALIZED_COST_PER_HECTARE,
                        HarvestSeasonComparisonMetric.REALIZED_REVENUE_PER_HECTARE,
                        HarvestSeasonComparisonMetric.REALIZED_PROFIT_PER_HECTARE));

        private final String label;
        private final List<HarvestSeasonComparisonMetric> metrics;

        MetricGroup(String label, List<HarvestSeasonComparisonMetric> metrics) {
            this.label = label;
            this.metrics = metrics;
        }

        public String label() {
            return label;
        }

        public List<HarvestSeasonComparisonMetric> metrics() {
            return metrics;
        }
    }
}
