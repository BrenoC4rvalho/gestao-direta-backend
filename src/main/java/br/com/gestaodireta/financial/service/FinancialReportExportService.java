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
import br.com.gestaodireta.financial.dto.FinancialReportTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialCommitmentStatusResponse;
import br.com.gestaodireta.financial.dto.FinancialHarvestSummaryResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialReportRepository;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.shared.pdf.PdfReportStyle;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialReportExportService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final int CATEGORY_LIMIT = 10;

    private static final java.awt.Color PRIMARY_COLOR = PdfReportStyle.PRIMARY_COLOR;

    private static final java.awt.Color INCOME_COLOR = new java.awt.Color(22, 130, 75);

    private static final java.awt.Color EXPENSE_COLOR = new java.awt.Color(183, 48, 48);

    private static final java.awt.Color MUTED_COLOR = PdfReportStyle.MUTED_COLOR;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final FinancialReportService financialReportService;
    private final FarmService farmService;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final HarvestSeasonRepository harvestSeasonRepository;
    private final FinancialReportRepository financialReportRepository;
    private final Clock clock;

    public FinancialReportExportService(
            FinancialReportService financialReportService,
            FarmService farmService,
            FinancialCategoryRepository financialCategoryRepository,
            HarvestSeasonRepository harvestSeasonRepository,
            Clock clock) {
        this(
                financialReportService,
                farmService,
                financialCategoryRepository,
                harvestSeasonRepository,
                null,
                clock);
    }

    @Autowired
    public FinancialReportExportService(
            FinancialReportService financialReportService,
            FarmService farmService,
            FinancialCategoryRepository financialCategoryRepository,
            HarvestSeasonRepository harvestSeasonRepository,
            FinancialReportRepository financialReportRepository,
            Clock clock) {
        this.financialReportService = financialReportService;
        this.farmService = farmService;
        this.financialCategoryRepository = financialCategoryRepository;
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.financialReportRepository = financialReportRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialReportExportFile exportPdf(FinancialReportFilter filter) {
        ExportData exportData = loadExportData(filter);

        return new FinancialReportExportFile(
                filename(exportData.report()), PDF_CONTENT_TYPE, createPdf(exportData));
    }

    @Transactional(readOnly = true)
    public FinancialReportExportFile exportXlsx(FinancialReportFilter filter) {
        ExportData exportData = loadExportData(filter);
        return new FinancialReportExportFile(
                xlsxFilename(exportData.report()), XLSX_CONTENT_TYPE, createWorkbook(exportData));
    }

    private ExportData loadExportData(FinancialReportFilter filter) {
        FinancialReportResponse report = financialReportService.getReport(filter);
        Farm farm = farmService.findEntityById(report.farmId());
        List<FinancialReportTransactionResponse> transactions =
                financialReportRepository == null
                        ? List.of()
                        : financialReportRepository.findAllTransactions(filter);
        FinancialCommitmentStatusResponse commitmentStatus =
                financialReportRepository == null
                        ? null
                        : financialReportService.commitmentStatus(filter);
        return new ExportData(
                filter,
                report,
                farm.getName(),
                categoryNames(filter.categoryIds()),
                harvestNames(filter.harvestSeasonIds()),
                transactions,
                commitmentStatus);
    }

    private byte[] createPdf(ExportData exportData) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 42);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);
            writer.setPageEvent(PdfReportStyle.footer("Relatório Financeiro"));
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
            addHarvests(document, exportData.report());
            addCommitmentStatus(document, exportData.commitmentStatus());
            addUnallocatedNotice(document, exportData.report());
            addTransactions(document, exportData.transactions());
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
        addEvolutionVisual(document, points);
    }

    private void addEvolutionVisual(
            Document document, List<FinancialEvolutionPointResponse> points) throws Exception {
        addSectionTitle(document, "Visualização da evolução");
        BigDecimal maximum =
                points.stream()
                        .map(point -> point.income().max(point.expense()))
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ONE);
        PdfPTable chart = table(new float[] {1.4f, 4.3f, 4.3f});
        addTableHeaders(chart, "Período", "Receitas", "Despesas");
        for (FinancialEvolutionPointResponse point : points) {
            addCell(chart, point.label(), Element.ALIGN_LEFT, bodyFont(7), null);
            chart.addCell(chartBarCell(point.income(), maximum, INCOME_COLOR));
            chart.addCell(chartBarCell(point.expense(), maximum, EXPENSE_COLOR));
        }
        document.add(chart);
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
        addCashFlowVisual(document, points);
    }

    private void addCashFlowVisual(
            Document document, List<FinancialCashFlowPointResponse> points) throws Exception {
        addSectionTitle(document, "Visualização do saldo acumulado");
        BigDecimal maximum =
                points.stream()
                        .map(point -> point.expectedBalance().abs().max(point.projectedBalance().abs()))
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ONE);
        PdfPTable chart = table(new float[] {1.4f, 4.3f, 4.3f});
        addTableHeaders(chart, "Período", "Saldo previsto", "Saldo projetado");
        for (FinancialCashFlowPointResponse point : points) {
            addCell(chart, point.label(), Element.ALIGN_LEFT, bodyFont(7), null);
            chart.addCell(chartBarCell(point.expectedBalance(), maximum, PRIMARY_COLOR));
            chart.addCell(chartBarCell(point.projectedBalance(), maximum, MUTED_COLOR));
        }
        document.add(chart);
    }

    private PdfPCell chartBarCell(BigDecimal value, BigDecimal maximum, java.awt.Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(3);
        cell.setBorderColor(PdfReportStyle.BORDER_COLOR);
        PdfPTable bars =
                new PdfPTable(
                        new float[] {
                            Math.max(1f, value.abs().floatValue()),
                            Math.max(1f, maximum.subtract(value.abs()).max(BigDecimal.ZERO).floatValue())
                        });
        bars.setWidthPercentage(100);
        PdfPCell filled = new PdfPCell();
        filled.setFixedHeight(7);
        filled.setBorder(0);
        filled.setBackgroundColor(color);
        PdfPCell empty = new PdfPCell();
        empty.setFixedHeight(7);
        empty.setBorder(0);
        empty.setBackgroundColor(new java.awt.Color(238, 242, 239));
        bars.addCell(filled);
        bars.addCell(empty);
        cell.addElement(bars);
        Paragraph label = new Paragraph(formatCurrency(value), bodyFont(7));
        label.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(label);
        return cell;
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

    private void addHarvests(Document document, FinancialReportResponse report) throws Exception {
        if (report.harvests().isEmpty()) {
            return;
        }
        addSectionTitle(document, "Análise por safra");
        PdfPTable table = table(new float[] {3, 2, 2, 2, 1});
        addTableHeaders(table, "Safra", "Receitas", "Despesas", "Resultado", "Margem");
        for (var harvest : report.harvests()) {
            addRow(
                    table,
                    List.of(
                            harvest.harvestSeasonName(),
                            formatCurrency(harvest.income()),
                            formatCurrency(harvest.expense()),
                            formatCurrency(harvest.profit()),
                            formatPercentage(harvest.marginPercentage())));
        }
        document.add(table);
    }

    private void addCommitmentStatus(
            Document document, FinancialCommitmentStatusResponse status) throws Exception {
        if (status == null) {
            return;
        }
        addSectionTitle(document, "Situação dos compromissos");
        PdfPTable history = table(new float[] {2, 2, 2, 2, 2, 2});
        addTableHeaders(
                history,
                "Histórico",
                "No prazo",
                "Em atraso",
                "Não classificável",
                "Taxa no prazo",
                "Tipo");
        addRow(
                history,
                List.of(
                        "Pagamentos",
                        status.history().expensesOnTime().toString(),
                        status.history().expensesLate().toString(),
                        status.history().unclassifiableExpenses().toString(),
                        formatNullablePercentage(status.history().expenseOnTimeRate()),
                        "Despesas"));
        addRow(
                history,
                List.of(
                        "Recebimentos",
                        status.history().incomesOnTime().toString(),
                        status.history().incomesLate().toString(),
                        status.history().unclassifiableIncomes().toString(),
                        formatNullablePercentage(status.history().incomeOnTimeRate()),
                        "Receitas"));
        document.add(history);

        PdfPTable current = table(new float[] {2, 2, 2, 2, 2});
        addTableHeaders(current, "Situação atual", "Quantidade", "Valor", "Tipo", "Referência");
        addRow(
                current,
                List.of(
                        "Vencidos",
                        status.current().overduePayables().count().toString(),
                        formatCurrency(status.current().overduePayables().amount()),
                        "Contas a pagar",
                        formatDate(status.current().referenceDate())));
        addRow(
                current,
                List.of(
                        "Vencidos",
                        status.current().overdueReceivables().count().toString(),
                        formatCurrency(status.current().overdueReceivables().amount()),
                        "Contas a receber",
                        formatDate(status.current().referenceDate())));
        document.add(current);

        PdfPTable upcoming = table(new float[] {2, 2, 2, 2});
        addTableHeaders(upcoming, "Próximos vencimentos", "Quantidade", "Valor", "Tipo");
        addRow(
                upcoming,
                List.of(
                        "Até " + status.upcoming().days() + " dias",
                        status.upcoming().payables().count().toString(),
                        formatCurrency(status.upcoming().payables().amount()),
                        "Contas a pagar"));
        addRow(
                upcoming,
                List.of(
                        "Até " + status.upcoming().days() + " dias",
                        status.upcoming().receivables().count().toString(),
                        formatCurrency(status.upcoming().receivables().amount()),
                        "Contas a receber"));
        document.add(upcoming);
    }

    private void addTransactions(
            Document document, List<FinancialReportTransactionResponse> transactions)
            throws Exception {
        document.newPage();
        addSectionTitle(document, "Movimentações detalhadas");
        if (transactions.isEmpty()) {
            document.add(new Paragraph("Nenhuma movimentação encontrada para o escopo.", bodyFont(9)));
            return;
        }
        PdfPTable table = table(new float[] {1.2f, 2.8f, 1.2f, 1.6f, 1.5f, 1.5f});
        addTableHeaders(table, "Data", "Descrição", "Tipo", "Categoria", "Safra", "Valor");
        for (FinancialReportTransactionResponse transaction : transactions) {
            addRow(
                    table,
                    List.of(
                            formatDate(transaction.referenceDate()),
                            valueOrNotInformed(transaction.description()),
                            typeLabel(transaction.type()),
                            valueOrNotInformed(transaction.categoryName()),
                            valueOrNotInformed(transaction.harvestSeasonName()),
                            formatCurrency(transaction.amount())));
        }
        document.add(table);
    }

    private byte[] createWorkbook(ExportData data) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            WorkbookStyles styles = new WorkbookStyles(workbook);
            addSummarySheet(workbook, styles, data);
            addEvolutionSheet(workbook, styles, data.report().evolution());
            addCashFlowSheet(workbook, styles, data.report());
            addCommitmentsSheet(workbook, styles, data.commitmentStatus(), data.report());
            addHarvestSummarySheet(workbook, styles, data.report());
            addHarvestParticipationSheet(workbook, styles, data.report());
            addTransactionsSheet(workbook, styles, data.farmName(), data.transactions());
            workbook.write(output);
            workbook.dispose();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate financial report spreadsheet", exception);
        }
    }

    private void addSummarySheet(Workbook workbook, WorkbookStyles styles, ExportData data) {
        Sheet sheet = workbook.createSheet("Resumo Financeiro");
        int row = 0;
        row = titleRow(sheet, row, "Relatório Financeiro", styles.title());
        row = labelValueRow(sheet, row, "Gerado em", DATE_TIME_FORMATTER.format(LocalDateTime.now(clock)), styles);
        row = labelValueRow(sheet, row, "Período", formatDate(data.report().startDate()) + " a " + formatDate(data.report().endDate()), styles);
        row = labelValueRow(sheet, row, "Fazenda", data.farmName(), styles);
        row = labelValueRow(sheet, row, "Safra", selectedFilterLabel(data.harvestNames()), styles);
        row = labelValueRow(sheet, row, "Categoria", selectedFilterLabel(data.categoryNames()), styles);
        row = labelValueRow(sheet, row, "Regime financeiro", basisLabel(data.report().basis()), styles);
        row = labelValueRow(sheet, row, "Granularidade", granularityLabel(data.filter().granularity()), styles);
        row++;
        row = titleRow(sheet, row, "Indicadores", styles.section());
        row = metricRow(sheet, row, "Receita total", data.report().summary().totalIncome(), styles.incomeCurrency());
        row = metricRow(sheet, row, "Despesa total", data.report().summary().totalExpense(), styles.expenseCurrency());
        row = metricRow(sheet, row, "Resultado financeiro", data.report().summary().netBalance(), styles.resultCurrency(data.report().summary().netBalance()));
        metricRow(sheet, row, "Margem financeira", data.report().summary().marginPercentage(), styles.percentage());
        finishSheet(sheet, 2, row + 1);
    }

    private void addEvolutionSheet(
            Workbook workbook, WorkbookStyles styles, List<FinancialEvolutionPointResponse> points) {
        Sheet sheet = workbook.createSheet("Evolução Financeira");
        String[] headers = {"Período", "Receitas realizadas", "Receitas projetadas", "Receitas vencidas", "Despesas realizadas", "Despesas projetadas", "Despesas vencidas", "Resultado realizado"};
        int row = headerRow(sheet, headers, styles);
        for (FinancialEvolutionPointResponse point : points) {
            Row current = sheet.createRow(row++);
            text(current, 0, point.label(), styles.text());
            number(current, 1, point.realizedIncome(), styles.currency());
            number(current, 2, point.projectedIncome(), styles.currency());
            number(current, 3, point.overdueIncome(), styles.currency());
            number(current, 4, point.realizedExpense(), styles.currency());
            number(current, 5, point.projectedExpense(), styles.currency());
            number(current, 6, point.overdueExpense(), styles.currency());
            number(current, 7, point.realizedResult(), styles.resultCurrency(point.realizedResult()));
        }
        addTotals(sheet, row, headers.length, styles, "Totais");
        finishSheet(sheet, headers.length, row);
    }

    private void addCashFlowSheet(Workbook workbook, WorkbookStyles styles, FinancialReportResponse report) {
        Sheet sheet = workbook.createSheet("Fluxo de Caixa");
        String[] headers = {"Período", "Entradas realizadas", "Saídas realizadas", "Variação do período", "Saldo acumulado previsto", "Saldo acumulado projetado"};
        int row = headerRow(sheet, headers, styles);
        for (var point : report.cashFlow().points()) {
            Row current = sheet.createRow(row++);
            text(current, 0, point.label(), styles.text());
            number(current, 1, point.realizedIncome(), styles.currency());
            number(current, 2, point.realizedExpense(), styles.currency());
            number(current, 3, point.realizedIncome().subtract(point.realizedExpense()), styles.currency());
            number(current, 4, point.expectedBalance(), styles.currency());
            number(current, 5, point.projectedBalance(), styles.currency());
        }
        addTotals(sheet, row, headers.length, styles, "Totais / saldo final");
        finishSheet(sheet, headers.length, row);
    }

    private void addCommitmentsSheet(
            Workbook workbook,
            WorkbookStyles styles,
            FinancialCommitmentStatusResponse status,
            FinancialReportResponse report) {
        Sheet sheet = workbook.createSheet("Compromissos");
        int row = titleRow(sheet, 0, "Situação dos compromissos", styles.title());
        row = headerRow(sheet, row, new String[] {"Grupo", "Indicador", "Quantidade", "Valor", "Taxa"}, styles);
        row = commitmentRow(sheet, row, "Histórico", "Pagamentos no prazo", status == null ? null : status.history().expensesOnTime(), null, status == null ? null : status.history().expenseOnTimeRate(), styles);
        row = commitmentRow(sheet, row, "Histórico", "Pagamentos em atraso", status == null ? null : status.history().expensesLate(), null, null, styles);
        row = commitmentRow(sheet, row, "Histórico", "Recebimentos no prazo", status == null ? null : status.history().incomesOnTime(), null, status == null ? null : status.history().incomeOnTimeRate(), styles);
        row = commitmentRow(sheet, row, "Histórico", "Recebimentos em atraso", status == null ? null : status.history().incomesLate(), null, null, styles);
        row = commitmentRow(sheet, row, "Situação atual", "Contas atualmente vencidas", report.commitments().overduePayableCount(), report.commitments().overduePayableAmount(), null, styles);
        row = commitmentRow(sheet, row, "Situação atual", "Recebimentos atualmente vencidos", report.commitments().overdueReceivableCount(), report.commitments().overdueReceivableAmount(), null, styles);
        if (status != null) {
            row = commitmentRow(sheet, row, "Próximos vencimentos", "Contas a pagar", status.upcoming().payables().count(), status.upcoming().payables().amount(), null, styles);
            commitmentRow(sheet, row, "Próximos vencimentos", "Contas a receber", status.upcoming().receivables().count(), status.upcoming().receivables().amount(), null, styles);
        }
        finishSheet(sheet, 5, row);
    }

    private void addHarvestSummarySheet(Workbook workbook, WorkbookStyles styles, FinancialReportResponse report) {
        Sheet sheet = workbook.createSheet("Resumo por Safra");
        String[] headers = {"Safra", "Receita", "Despesa", "Resultado", "Margem", "Participação na receita"};
        int row = headerRow(sheet, headers, styles);
        for (var harvest : report.harvests()) {
            Row current = sheet.createRow(row++);
            text(current, 0, harvest.harvestSeasonName(), styles.text());
            number(current, 1, harvest.income(), styles.incomeCurrency());
            number(current, 2, harvest.expense(), styles.expenseCurrency());
            number(current, 3, harvest.profit(), styles.resultCurrency(harvest.profit()));
            number(current, 4, harvest.marginPercentage(), styles.percentage());
            number(current, 5, harvest.details() == null ? BigDecimal.ZERO : harvest.details().periodRevenueShare(), styles.percentage());
        }
        addTotals(sheet, row, headers.length, styles, "Totais");
        finishSheet(sheet, headers.length, row);
    }

    private void addHarvestParticipationSheet(Workbook workbook, WorkbookStyles styles, FinancialReportResponse report) {
        Sheet sheet = workbook.createSheet("Participação por Safra");
        int row = titleRow(sheet, 0, "Participação por safra", styles.title());
        row = participationTable(sheet, row, "Receitas", report.harvests(), styles, 0);
        row = participationTable(sheet, row + 1, "Despesas", report.harvests(), styles, 1);
        participationTable(sheet, row + 1, "Resultado", report.harvests(), styles, 2);
        finishSheet(sheet, 3, row + 3);
    }

    private int participationTable(
            Sheet sheet,
            int row,
            String title,
            List<FinancialHarvestSummaryResponse> harvests,
            WorkbookStyles styles,
            int mode) {
        row = titleRow(sheet, row, title, styles.section());
        row = headerRow(sheet, row, new String[] {"Safra", "Valor", "Participação"}, styles);
        BigDecimal total = BigDecimal.ZERO;
        for (FinancialHarvestSummaryResponse item : harvests) {
            if (mode == 2 && item.profit().signum() <= 0) {
                continue;
            }
            total = total.add(mode == 0 ? item.income() : mode == 1 ? item.expense() : item.profit());
        }
        for (FinancialHarvestSummaryResponse item : harvests) {
            BigDecimal amount = mode == 0 ? item.income() : mode == 1 ? item.expense() : item.profit();
            if ((mode == 0 || mode == 1) && amount.signum() <= 0) {
                continue;
            }
            if (mode == 2 && amount.signum() <= 0) {
                continue;
            }
            Row current = sheet.createRow(row++);
            text(current, 0, item.harvestSeasonName(), styles.text());
            number(current, 1, amount, styles.currency());
            number(current, 2, total.signum() == 0 ? BigDecimal.ZERO : amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, java.math.RoundingMode.HALF_UP), styles.percentage());
        }
        if (mode == 2 && harvests.stream().anyMatch(item -> item.profit().signum() < 0)) {
            Row note = sheet.createRow(row++);
            text(note, 0, "Resultados negativos (sem participação)", styles.label());
            text(note, 2, "Ver valor no resumo", styles.text());
        }
        return row;
    }

    private void addTransactionsSheet(
            Workbook workbook,
            WorkbookStyles styles,
            String farmName,
            List<FinancialReportTransactionResponse> transactions) {
        Sheet sheet = workbook.createSheet("Movimentações");
        String[] headers = {"Data de referência", "Data", "Tipo", "Descrição", "Categoria", "Fazenda", "Safra", "Valor", "Status", "Vencimento", "Pagamento/recebimento"};
        int row = headerRow(sheet, headers, styles);
        for (FinancialReportTransactionResponse transaction : transactions) {
            Row current = sheet.createRow(row++);
            date(current, 0, transaction.referenceDate(), styles.date());
            date(current, 1, transaction.transactionDate(), styles.date());
            text(current, 2, typeLabel(transaction.type()), styles.text());
            text(current, 3, safeSpreadsheetText(transaction.description()), styles.text());
            text(current, 4, safeSpreadsheetText(valueOrNotInformed(transaction.categoryName())), styles.text());
            text(current, 5, safeSpreadsheetText(farmName), styles.text());
            text(current, 6, safeSpreadsheetText(valueOrNotInformed(transaction.harvestSeasonName())), styles.text());
            number(current, 7, transaction.amount(), styles.currency());
            text(current, 8, transaction.paymentStatus().name(), styles.text());
            date(current, 9, transaction.dueDate(), styles.date());
            date(current, 10, transaction.paidAt(), styles.date());
        }
        finishSheet(sheet, headers.length, row);
    }

    private int titleRow(Sheet sheet, int rowIndex, String value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return rowIndex + 1;
    }

    private int labelValueRow(
            Sheet sheet, int rowIndex, String label, String value, WorkbookStyles styles) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label, styles.label());
        text(row, 1, valueOrNotInformed(value), styles.text());
        return rowIndex + 1;
    }

    private int metricRow(
            Sheet sheet, int rowIndex, String label, BigDecimal value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label, style);
        number(row, 1, value, style);
        return rowIndex + 1;
    }

    private int headerRow(Sheet sheet, String[] headers, WorkbookStyles styles) {
        return headerRow(sheet, 0, headers, styles);
    }

    private int headerRow(Sheet sheet, int rowIndex, String[] headers, WorkbookStyles styles) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < headers.length; index++) {
            text(row, index, headers[index], styles.header());
        }
        return rowIndex + 1;
    }

    private int commitmentRow(
            Sheet sheet,
            int rowIndex,
            String group,
            String label,
            Long count,
            BigDecimal amount,
            BigDecimal rate,
            WorkbookStyles styles) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, group, styles.text());
        text(row, 1, label, styles.text());
        if (count != null) {
            Cell cell = row.createCell(2);
            cell.setCellValue(count);
            cell.setCellStyle(styles.integer());
        }
        if (amount != null) {
            number(row, 3, amount, styles.currency());
        }
        if (rate != null) {
            number(row, 4, rate, styles.percentage());
        }
        return rowIndex + 1;
    }

    private void addTotals(
            Sheet sheet, int rowIndex, int columnCount, WorkbookStyles styles, String label) {
        if (rowIndex <= 1) {
            return;
        }
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label, styles.total());
        for (int column = 1; column < columnCount; column++) {
            Cell cell = row.createCell(column);
            cell.setCellFormula("SUM(" + columnName(column) + "2:" + columnName(column) + rowIndex + ")");
            cell.setCellStyle(styles.totalCurrency());
        }
    }

    private String columnName(int column) {
        StringBuilder result = new StringBuilder();
        int value = column + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            result.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return result.toString();
    }

    private void finishSheet(Sheet sheet, int columnCount, int lastRow) {
        if (lastRow > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, lastRow - 1), 0, columnCount - 1));
        }
        sheet.createFreezePane(0, 1);
        int[] widths = {22, 24, 22, 28, 22, 22, 24, 20, 20, 18, 22};
        for (int column = 0; column < columnCount; column++) {
            sheet.setColumnWidth(column, widths[Math.min(column, widths.length - 1)] * 256);
        }
    }

    private void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(valueOrNotInformed(value));
        cell.setCellStyle(style);
    }

    private void number(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(style);
    }

    private void date(Row row, int column, LocalDate value, CellStyle style) {
        if (value == null) {
            text(row, column, "", style);
            return;
        }
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String selectedFilterLabel(List<String> values) {
        return values.isEmpty() ? "Todas" : String.join(", ", values);
    }

    private String valueOrNotInformed(String value) {
        return value == null || value.isBlank() ? "Não informado" : value;
    }

    private String safeSpreadsheetText(String value) {
        String safe = valueOrNotInformed(value);
        return safe.startsWith("=") || safe.startsWith("+") || safe.startsWith("-") || safe.startsWith("@")
                ? "'" + safe
                : safe;
    }

    private String formatNullablePercentage(BigDecimal value) {
        return value == null ? "Sem dados" : formatPercentage(value);
    }

    private static final class WorkbookStyles {
        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle header;
        private final CellStyle label;
        private final CellStyle text;
        private final CellStyle integer;
        private final CellStyle date;
        private final CellStyle currency;
        private final CellStyle incomeCurrency;
        private final CellStyle expenseCurrency;
        private final CellStyle resultCurrency;
        private final CellStyle percentage;
        private final CellStyle total;
        private final CellStyle totalCurrency;

        private WorkbookStyles(Workbook workbook) {
            title = style(workbook, true, (short) 14, org.apache.poi.ss.usermodel.IndexedColors.DARK_GREEN.getIndex());
            section = style(workbook, true, (short) 11, org.apache.poi.ss.usermodel.IndexedColors.LIGHT_GREEN.getIndex());
            header = style(workbook, true, (short) 10, org.apache.poi.ss.usermodel.IndexedColors.DARK_GREEN.getIndex());
            label = style(workbook, true, (short) 10, org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            text = style(workbook, false, (short) 10, org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            integer = text;
            date = style(workbook, false, (short) 10, org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            date.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/MM/yyyy"));
            currency = style(workbook, false, (short) 10, org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            currency.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("R$ #,##0.00"));
            incomeCurrency = currency;
            expenseCurrency = currency;
            resultCurrency = currency;
            percentage = style(workbook, false, (short) 10, org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            percentage.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0.00%"));
            total = style(workbook, true, (short) 10, org.apache.poi.ss.usermodel.IndexedColors.LIGHT_YELLOW.getIndex());
            totalCurrency = style(workbook, true, (short) 10, org.apache.poi.ss.usermodel.IndexedColors.LIGHT_YELLOW.getIndex());
            totalCurrency.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("R$ #,##0.00"));
        }

        private static CellStyle style(Workbook workbook, boolean bold, short size, short fill) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(bold);
            font.setFontHeightInPoints(size);
            if (fill == org.apache.poi.ss.usermodel.IndexedColors.DARK_GREEN.getIndex()) {
                font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            }
            style.setFont(font);
            style.setFillForegroundColor(fill);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderBottom(BorderStyle.THIN);
            return style;
        }

        CellStyle title() { return title; }
        CellStyle section() { return section; }
        CellStyle header() { return header; }
        CellStyle label() { return label; }
        CellStyle text() { return text; }
        CellStyle integer() { return integer; }
        CellStyle date() { return date; }
        CellStyle currency() { return currency; }
        CellStyle incomeCurrency() { return incomeCurrency; }
        CellStyle expenseCurrency() { return expenseCurrency; }
        CellStyle resultCurrency(BigDecimal value) { return resultCurrency; }
        CellStyle percentage() { return percentage; }
        CellStyle total() { return total; }
        CellStyle totalCurrency() { return totalCurrency; }
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
        cell.setBorderColor(PdfReportStyle.BORDER_COLOR);
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
        cell.setBorderColor(PdfReportStyle.BORDER_COLOR);
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
        if (financialCategoryRepository == null || categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return financialCategoryRepository.findAllById(categoryIds).stream()
                .map(FinancialCategory::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> harvestNames(List<Long> harvestIds) {
        if (harvestSeasonRepository == null || harvestIds == null || harvestIds.isEmpty()) {
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

    private String xlsxFilename(FinancialReportResponse report) {
        return "relatorio-financeiro-" + report.startDate() + "-a-" + report.endDate() + ".xlsx";
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
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                .format(value == null ? BigDecimal.ZERO : value);
    }

    private String formatPercentage(BigDecimal value) {
        return new java.text.DecimalFormat(
                                "0.0",
                                java.text.DecimalFormatSymbols.getInstance(new Locale("pt", "BR")))
                        .format(value == null ? BigDecimal.ZERO : value)
                + "%";
    }

    private String formatDate(LocalDate date) {
        return date == null ? "Não informado" : DATE_FORMATTER.format(date);
    }

    private String typeLabel(TransactionType type) {
        return TransactionType.INCOME.equals(type) ? "Receita" : "Despesa";
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
            List<String> harvestNames,
            List<FinancialReportTransactionResponse> transactions,
            FinancialCommitmentStatusResponse commitmentStatus) {}
}
