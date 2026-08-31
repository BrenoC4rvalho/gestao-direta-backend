package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialCashFlowPointResponse;
import br.com.gestaodireta.financial.dto.FinancialCategorySummaryGroupResponse;
import br.com.gestaodireta.financial.dto.FinancialCategorySummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialEvolutionPointResponse;
import br.com.gestaodireta.financial.dto.FinancialReportExportFile;
import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportIndicatorsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportResponse;
import br.com.gestaodireta.financial.dto.FinancialReportSummaryResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
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
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialReportExportService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final int CATEGORY_LIMIT = 10;

    private static final java.awt.Color PRIMARY_COLOR = new java.awt.Color(28, 100, 69);

    private static final java.awt.Color INCOME_COLOR = new java.awt.Color(22, 130, 75);

    private static final java.awt.Color EXPENSE_COLOR = new java.awt.Color(183, 48, 48);

    private static final java.awt.Color MUTED_COLOR = new java.awt.Color(88, 99, 112);

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final FinancialReportService financialReportService;
    private final FarmService farmService;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final HarvestSeasonRepository harvestSeasonRepository;
    private final Clock clock;

    public FinancialReportExportService(
            FinancialReportService financialReportService,
            FarmService farmService,
            FinancialCategoryRepository financialCategoryRepository,
            HarvestSeasonRepository harvestSeasonRepository,
            Clock clock) {
        this.financialReportService = financialReportService;
        this.farmService = farmService;
        this.financialCategoryRepository = financialCategoryRepository;
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialReportExportFile exportPdf(FinancialReportFilter filter) {
        FinancialReportResponse report = financialReportService.getReport(filter);
        Farm farm = farmService.findEntityById(report.farmId());
        ExportData exportData =
                new ExportData(
                        filter,
                        report,
                        farm.getName(),
                        categoryNames(filter.categoryIds()),
                        harvestNames(filter.harvestSeasonIds()));

        return new FinancialReportExportFile(
                filename(report), PDF_CONTENT_TYPE, createPdf(exportData));
    }

    private byte[] createPdf(ExportData exportData) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 42);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);
            writer.setPageEvent(new ReportFooter());
            document.addTitle("Relatório Financeiro");
            document.addAuthor("Gestão Direta");
            document.open();

            addHeader(document, exportData);
            if (!hasData(exportData.report())) {
                document.add(new Paragraph(" "));
                document.add(
                        new Paragraph(
                                "Nenhuma movimentação encontrada para os filtros selecionados.",
                                bodyFont(11)));
                document.close();
                return output.toByteArray();
            }

            addExecutiveSummary(document, exportData.report().summary());
            addRealizedAndProjected(document, exportData.report().summary());
            addCommitments(document, exportData.report());
            addHighlights(document, exportData.report().indicators());
            addEvolution(document, exportData.report().evolution());
            addCashFlow(document, exportData.report().cashFlow().points());
            addCategories(document, exportData.report().categories());
            addUnallocatedNotice(document, exportData.report());
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate financial report PDF", exception);
        }
    }

    private void addHeader(Document document, ExportData data) throws Exception {
        Paragraph product = new Paragraph("GESTÃO DIRETA", titleFont(16));
        product.setSpacingAfter(3);
        document.add(product);

        Paragraph title = new Paragraph("RELATÓRIO FINANCEIRO", subtitleFont(12));
        title.setSpacingAfter(12);
        document.add(title);

        PdfPTable metadata = new PdfPTable(new float[] {1, 2});
        metadata.setWidthPercentage(100);
        metadata.setSpacingAfter(12);
        addMetadata(metadata, "Fazenda", data.farmName());
        addMetadata(
                metadata,
                "Período",
                formatDate(data.report().startDate())
                        + " a "
                        + formatDate(data.report().endDate()));
        addMetadata(metadata, "Regime", basisLabel(data.report().basis()));
        addMetadata(metadata, "Granularidade", granularityLabel(data.filter().granularity()));
        if (!data.harvestNames().isEmpty()) {
            addMetadata(metadata, "Safras", String.join(", ", data.harvestNames()));
        }
        if (!data.categoryNames().isEmpty()) {
            addMetadata(metadata, "Categorias", String.join(", ", data.categoryNames()));
        }
        addMetadata(metadata, "Gerado em", DATE_TIME_FORMATTER.format(LocalDateTime.now(clock)));
        document.add(metadata);
    }

    private void addExecutiveSummary(Document document, FinancialReportSummaryResponse summary)
            throws Exception {
        addSectionTitle(document, "Resumo executivo");
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        addMetric(table, "Receitas totais", formatCurrency(summary.totalIncome()), INCOME_COLOR);
        addMetric(table, "Despesas totais", formatCurrency(summary.totalExpense()), EXPENSE_COLOR);
        addMetric(
                table,
                "Saldo líquido",
                formatCurrency(summary.netBalance()),
                valueColor(summary.netBalance()));
        addMetric(table, "Margem", formatPercentage(summary.marginPercentage()), PRIMARY_COLOR);
        document.add(table);
    }

    private void addRealizedAndProjected(Document document, FinancialReportSummaryResponse summary)
            throws Exception {
        addSectionTitle(document, "Realizado e projetado");
        PdfPTable table = table(new float[] {2, 2, 2, 2});
        addTableHeaders(table, "Visão", "Receitas", "Despesas", "Resultado");
        addRow(
                table,
                List.of(
                        "Realizado",
                        formatCurrency(summary.realizedIncome()),
                        formatCurrency(summary.realizedExpense()),
                        formatCurrency(
                                summary.realizedIncome().subtract(summary.realizedExpense()))));
        addRow(
                table,
                List.of(
                        "Projetado",
                        formatCurrency(summary.projectedIncome()),
                        formatCurrency(summary.projectedExpense()),
                        formatCurrency(
                                summary.projectedIncome().subtract(summary.projectedExpense()))));
        document.add(table);
    }

    private void addCommitments(Document document, FinancialReportResponse report)
            throws Exception {
        addSectionTitle(document, "Compromissos financeiros");
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        addMetric(
                table,
                "Contas a receber",
                formatCurrency(report.commitments().accountsReceivable()),
                INCOME_COLOR);
        addMetric(
                table,
                "Contas a pagar",
                formatCurrency(report.commitments().accountsPayable()),
                EXPENSE_COLOR);
        addMetric(
                table,
                "Vencido a receber (" + report.commitments().overdueReceivableCount() + ")",
                formatCurrency(report.commitments().overdueReceivableAmount()),
                EXPENSE_COLOR);
        addMetric(
                table,
                "Vencido a pagar (" + report.commitments().overduePayableCount() + ")",
                formatCurrency(report.commitments().overduePayableAmount()),
                EXPENSE_COLOR);
        document.add(table);

        if (!report.commitments().next30DaysAvailable()) {
            return;
        }

        addSectionTitle(document, "Próximos 30 dias");
        PdfPTable next30Table = table(new float[] {2, 2, 2, 2});
        BigDecimal receivable = report.commitments().next30DaysReceivable();
        BigDecimal payable = report.commitments().next30DaysPayable();
        addTableHeaders(next30Table, "Recebimentos", "Pagamentos", "Fluxo líquido", "Cobertura");
        addRow(
                next30Table,
                List.of(
                        formatCurrency(receivable),
                        formatCurrency(payable),
                        formatCurrency(receivable.subtract(payable)),
                        coverage(receivable, payable)));
        document.add(next30Table);
    }

    private void addHighlights(Document document, FinancialReportIndicatorsResponse indicators)
            throws Exception {
        addSectionTitle(document, "Destaques do período");
        PdfPTable table = table(new float[] {2, 3});
        addTableHeaders(table, "Indicador", "Valor");
        addRow(table, List.of("Maior receita", periodAmount(indicators.highestIncomePeriod())));
        addRow(table, List.of("Maior despesa", periodAmount(indicators.highestExpensePeriod())));
        addRow(table, List.of("Melhor resultado", periodAmount(indicators.bestBalancePeriod())));
        addRow(table, List.of("Período crítico", periodAmount(indicators.criticalPeriod())));
        addRow(
                table,
                List.of(
                        "Categoria com maior despesa",
                        indicators.highestExpenseCategory() == null
                                ? "—"
                                : indicators.highestExpenseCategory().categoryName()
                                        + " · "
                                        + formatCurrency(
                                                indicators.highestExpenseCategory().amount())));
        addRow(
                table,
                List.of(
                        "Safra mais lucrativa",
                        indicators.mostProfitableHarvest() == null
                                ? "—"
                                : indicators.mostProfitableHarvest().harvestSeasonName()
                                        + " · "
                                        + formatCurrency(
                                                indicators.mostProfitableHarvest().profit())));
        document.add(table);
    }

    private void addEvolution(Document document, List<FinancialEvolutionPointResponse> points)
            throws Exception {
        if (points.isEmpty()) {
            return;
        }

        addSectionTitle(document, "Evolução financeira");
        PdfPTable table = table(new float[] {2, 2, 2, 2, 2});
        addTableHeaders(
                table, "Período", "Receitas", "Despesas", "Resultado", "Resultado realizado");
        for (FinancialEvolutionPointResponse point : points) {
            addRow(
                    table,
                    List.of(
                            point.label(),
                            formatCurrency(point.income()),
                            formatCurrency(point.expense()),
                            formatCurrency(point.netBalance()),
                            formatCurrency(point.realizedResult())));
        }
        document.add(table);
    }

    private void addCashFlow(Document document, List<FinancialCashFlowPointResponse> points)
            throws Exception {
        if (points.isEmpty()) {
            return;
        }

        addSectionTitle(document, "Fluxo de caixa acumulado");
        PdfPTable table = table(new float[] {2, 2, 2, 2, 2});
        addTableHeaders(
                table,
                "Período",
                "Saldo previsto",
                "Projetado c/ atrasos",
                "Vencido a receber",
                "Vencido a pagar");
        for (FinancialCashFlowPointResponse point : points) {
            addRow(
                    table,
                    List.of(
                            point.label(),
                            formatCurrency(point.expectedBalance()),
                            formatCurrency(point.projectedBalance()),
                            formatCurrency(point.overdueIncome()),
                            formatCurrency(point.overdueExpense())));
        }
        document.add(table);
    }

    private void addCategories(
            Document document, List<FinancialCategorySummaryGroupResponse> categoryGroups)
            throws Exception {
        addCategoryGroup(
                document,
                categoryGroup(categoryGroups, TransactionType.EXPENSE),
                "Top categorias de despesas");
        addCategoryGroup(
                document,
                categoryGroup(categoryGroups, TransactionType.INCOME),
                "Top categorias de receitas");
    }

    private void addCategoryGroup(
            Document document, FinancialCategorySummaryGroupResponse group, String title)
            throws Exception {
        if (group == null || group.items().isEmpty()) {
            return;
        }

        addSectionTitle(document, title);
        PdfPTable table = table(new float[] {3, 2, 1, 1});
        addTableHeaders(table, "Categoria", "Valor", "Participação", "Movimentações");
        group.items().stream()
                .limit(CATEGORY_LIMIT)
                .forEach(
                        item ->
                                addRow(
                                        table,
                                        List.of(
                                                item.categoryName(),
                                                formatCurrency(item.amount()),
                                                formatPercentage(item.percentage()),
                                                Long.toString(item.transactionCount()))));
        document.add(table);

        if (group.items().size() <= CATEGORY_LIMIT) {
            return;
        }

        List<FinancialCategorySummaryResponse> remaining =
                group.items().subList(CATEGORY_LIMIT, group.items().size());
        BigDecimal remainingAmount =
                remaining.stream()
                        .map(FinancialCategorySummaryResponse::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        document.add(
                new Paragraph(
                        "Demais categorias: "
                                + remaining.size()
                                + " · "
                                + formatCurrency(remainingAmount),
                        bodyFont(8)));
    }

    private void addUnallocatedNotice(Document document, FinancialReportResponse report)
            throws Exception {
        if (!FinancialReportBasis.CASH.equals(report.basis())
                || report.unallocated().transactionCount() == 0) {
            return;
        }

        Paragraph notice =
                new Paragraph(
                        "Observação: "
                                + report.unallocated().transactionCount()
                                + " movimentação(ões) em aberto sem data de vencimento não foram incluídas nos indicadores por período.",
                        bodyFont(8));
        notice.setSpacingBefore(8);
        document.add(notice);
    }

    private void addSectionTitle(Document document, String value) throws Exception {
        Paragraph title = new Paragraph(value, subtitleFont(11));
        title.setSpacingBefore(10);
        title.setSpacingAfter(5);
        document.add(title);
    }

    private PdfPTable table(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSpacingAfter(10);
        return table;
    }

    private void addMetadata(PdfPTable table, String label, String value) {
        addCell(table, label, Element.ALIGN_LEFT, labelFont(), null);
        addCell(table, value, Element.ALIGN_LEFT, bodyFont(9), null);
    }

    private void addMetric(PdfPTable table, String label, String value, java.awt.Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBorderColor(new java.awt.Color(220, 225, 220));
        cell.addElement(new Paragraph(label, bodyFont(8)));
        cell.addElement(new Paragraph(value, valueFont(color)));
        table.addCell(cell);
    }

    private void addTableHeaders(PdfPTable table, String... headers) {
        for (String header : headers) {
            addCell(table, header, Element.ALIGN_CENTER, headerFont(), PRIMARY_COLOR);
        }
    }

    private void addRow(PdfPTable table, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            int alignment = index == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT;
            addCell(table, values.get(index), alignment, bodyFont(8), null);
        }
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
        cell.setBorderColor(new java.awt.Color(220, 225, 220));
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        table.addCell(cell);
    }

    private FinancialCategorySummaryGroupResponse categoryGroup(
            List<FinancialCategorySummaryGroupResponse> groups, TransactionType type) {
        return groups.stream().filter(group -> type.equals(group.type())).findFirst().orElse(null);
    }

    private boolean hasData(FinancialReportResponse report) {
        return report.evolution().stream().anyMatch(point -> point.transactionCount() > 0)
                || report.unallocated().transactionCount() > 0;
    }

    private List<String> categoryNames(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return financialCategoryRepository.findAllById(categoryIds).stream()
                .map(FinancialCategory::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> harvestNames(List<Long> harvestIds) {
        if (harvestIds == null || harvestIds.isEmpty()) {
            return List.of();
        }
        return harvestSeasonRepository.findAllById(harvestIds).stream()
                .map(HarvestSeason::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String filename(FinancialReportResponse report) {
        return "relatorio-financeiro-" + report.startDate() + "-a-" + report.endDate() + ".pdf";
    }

    private String granularityLabel(FinancialReportGranularity granularity) {
        return FinancialReportGranularity.QUARTERLY.equals(granularity) ? "Trimestral" : "Mensal";
    }

    private String basisLabel(FinancialReportBasis basis) {
        return FinancialReportBasis.CASH.equals(basis) ? "Caixa" : "Competência";
    }

    private String periodAmount(
            br.com.gestaodireta.financial.dto.FinancialReportPeriodIndicatorResponse indicator) {
        return indicator == null
                ? "—"
                : indicator.label() + " · " + formatCurrency(indicator.amount());
    }

    private String coverage(BigDecimal receivable, BigDecimal payable) {
        if (payable.signum() == 0) {
            return receivable.signum() > 0 ? "Sem compromissos" : "—";
        }
        return new java.text.DecimalFormat(
                                "0.00",
                                java.text.DecimalFormatSymbols.getInstance(new Locale("pt", "BR")))
                        .format(receivable.divide(payable, 4, java.math.RoundingMode.HALF_UP))
                + "x";
    }

    private String formatCurrency(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value);
    }

    private String formatPercentage(BigDecimal value) {
        return new java.text.DecimalFormat(
                                "0.0",
                                java.text.DecimalFormatSymbols.getInstance(new Locale("pt", "BR")))
                        .format(value)
                + "%";
    }

    private String formatDate(LocalDate date) {
        return DATE_FORMATTER.format(date);
    }

    private java.awt.Color valueColor(BigDecimal value) {
        return value.signum() < 0 ? EXPENSE_COLOR : INCOME_COLOR;
    }

    private org.openpdf.text.Font titleFont(int size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, PRIMARY_COLOR);
    }

    private org.openpdf.text.Font subtitleFont(int size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, PRIMARY_COLOR);
    }

    private org.openpdf.text.Font labelFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MUTED_COLOR);
    }

    private org.openpdf.text.Font headerFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, java.awt.Color.WHITE);
    }

    private org.openpdf.text.Font bodyFont(int size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, java.awt.Color.BLACK);
    }

    private org.openpdf.text.Font valueFont(java.awt.Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, color);
    }

    private record ExportData(
            FinancialReportFilter filter,
            FinancialReportResponse report,
            String farmName,
            List<String> categoryNames,
            List<String> harvestNames) {}

    private static class ReportFooter extends PdfPageEventHelper {

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            org.openpdf.text.Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED_COLOR);
            Phrase footer =
                    new Phrase(
                            "Gestão Direta · Relatório Financeiro · Página "
                                    + writer.getPageNumber(),
                            font);
            org.openpdf.text.pdf.ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_CENTER,
                    footer,
                    (document.left() + document.right()) / 2,
                    22,
                    0);
        }
    }
}
