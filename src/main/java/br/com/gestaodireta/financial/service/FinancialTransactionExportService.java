package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialTransactionExportFile;
import br.com.gestaodireta.financial.dto.FinancialTransactionFilterRequest;
import br.com.gestaodireta.financial.dto.FinancialTransactionResponse;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialTransactionExportService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FinancialTransactionService financialTransactionService;
    private final FarmService farmService;
    private final Clock clock;

    public FinancialTransactionExportService(
            FinancialTransactionService financialTransactionService,
            FarmService farmService,
            Clock clock) {
        this.financialTransactionService = financialTransactionService;
        this.farmService = farmService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialTransactionExportFile exportXlsx(
            FinancialTransactionFilterRequest filterRequest) {
        ExportData exportData = loadExportData(filterRequest);
        return new FinancialTransactionExportFile(
                filename(exportData.filter(), "xlsx"),
                XLSX_CONTENT_TYPE,
                createWorkbook(exportData));
    }

    @Transactional(readOnly = true)
    public FinancialTransactionExportFile exportPdf(
            FinancialTransactionFilterRequest filterRequest) {
        ExportData exportData = loadExportData(filterRequest);
        return new FinancialTransactionExportFile(
                filename(exportData.filter(), "pdf"), PDF_CONTENT_TYPE, createPdf(exportData));
    }

    private ExportData loadExportData(FinancialTransactionFilterRequest filterRequest) {
        List<FinancialTransactionResponse> transactions =
                financialTransactionService.findAllForExport(filterRequest);
        Farm farm = farmService.findEntityById(filterRequest.farmId());
        return new ExportData(filterRequest, farm.getName(), transactions);
    }

    private byte[] createWorkbook(ExportData exportData) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Movimentações");
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook);
            CellStyle currencyStyle = currencyStyle(workbook);
            String[] headers = {
                "Data",
                "Descrição",
                "Tipo",
                "Categoria",
                "Safra",
                "Forma de pagamento",
                "Status",
                "Valor",
                "Vencimento",
                "Data de pagamento"
            };

            Row header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(headers[column]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (FinancialTransactionResponse transaction : exportData.transactions()) {
                Row row = sheet.createRow(rowIndex++);
                dateCell(row, 0, transaction.transactionDate(), dateStyle);
                textCell(row, 1, safeSpreadsheetText(transaction.description()));
                textCell(row, 2, typeLabel(transaction.type()));
                textCell(
                        row,
                        3,
                        safeSpreadsheetText(valueOrNotInformed(transaction.categoryName())));
                textCell(
                        row,
                        4,
                        safeSpreadsheetText(valueOrNotInformed(transaction.harvestSeasonName())));
                textCell(row, 5, paymentMethodLabel(transaction.paymentMethod()));
                textCell(row, 6, paymentStatusLabel(transaction.status()));
                Cell amountCell = row.createCell(7);
                amountCell.setCellValue(transaction.amount().doubleValue());
                amountCell.setCellStyle(currencyStyle);
                dateCell(row, 8, transaction.dueDate(), dateStyle);
                dateCell(row, 9, transaction.paidAt(), dateStyle);
            }

            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
            sheet.createFreezePane(0, 1);
            int[] widths = {14, 38, 14, 24, 24, 24, 14, 16, 14, 18};
            for (int column = 0; column < widths.length; column++) {
                sheet.setColumnWidth(column, widths[column] * 256);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to generate financial transactions spreadsheet", exception);
        }
    }

    private byte[] createPdf(ExportData exportData) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 28, 28, 28, 32);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);
            document.addTitle("Movimentações financeiras");
            document.addAuthor("Gestão Direta");
            document.open();

            org.openpdf.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
            org.openpdf.text.Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            document.add(new Paragraph("Gestão Direta", titleFont));
            document.add(
                    new Paragraph(
                            "Movimentações financeiras",
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            document.add(new Paragraph("Fazenda: " + exportData.farmName(), bodyFont));
            String filters = filterSummary(exportData.filter());
            if (!filters.isBlank()) {
                document.add(new Paragraph(filters, bodyFont));
            }
            document.add(
                    new Paragraph(
                            "Gerado em: " + DATE_TIME_FORMATTER.format(LocalDateTime.now(clock)),
                            bodyFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[] {10, 30, 11, 17, 16, 10, 12});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            addPdfHeaders(table);
            for (FinancialTransactionResponse transaction : exportData.transactions()) {
                addPdfCell(
                        table,
                        formatDate(transaction.transactionDate()),
                        Element.ALIGN_LEFT,
                        bodyFont);
                addPdfCell(table, transaction.description(), Element.ALIGN_LEFT, bodyFont);
                addPdfCell(table, typeLabel(transaction.type()), Element.ALIGN_LEFT, bodyFont);
                addPdfCell(
                        table,
                        valueOrNotInformed(transaction.categoryName()),
                        Element.ALIGN_LEFT,
                        bodyFont);
                addPdfCell(
                        table,
                        valueOrNotInformed(transaction.harvestSeasonName()),
                        Element.ALIGN_LEFT,
                        bodyFont);
                addPdfCell(
                        table, formatCurrency(transaction.amount()), Element.ALIGN_RIGHT, bodyFont);
                addPdfCell(
                        table,
                        paymentStatusLabel(transaction.status()),
                        Element.ALIGN_LEFT,
                        bodyFont);
            }
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to generate financial transactions PDF", exception);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(
                org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle dateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(
                workbook.getCreationHelper().createDataFormat().getFormat("dd/MM/yyyy"));
        return style;
    }

    private CellStyle currencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(
                workbook.getCreationHelper().createDataFormat().getFormat("R$ #,##0.00"));
        return style;
    }

    private void dateCell(Row row, int column, LocalDate value, CellStyle dateStyle) {
        if (value == null) {
            textCell(row, column, "");
            return;
        }

        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(dateStyle);
    }

    private void textCell(Row row, int column, String value) {
        row.createCell(column).setCellValue(value);
    }

    private void addPdfHeaders(PdfPTable table) {
        org.openpdf.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        String[] headers = {"Data", "Descrição", "Tipo", "Categoria", "Safra", "Valor", "Status"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addPdfCell(
            PdfPTable table, String value, int alignment, org.openpdf.text.Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private String filterSummary(FinancialTransactionFilterRequest filter) {
        StringBuilder summary = new StringBuilder();
        if (filter.transactionDateStart() != null || filter.transactionDateEnd() != null) {
            summary.append("Período: ")
                    .append(formatDate(filter.transactionDateStart()))
                    .append(" a ")
                    .append(formatDate(filter.transactionDateEnd()));
        }
        if (filter.type() != null) {
            appendFilter(summary, "Tipo: " + typeLabel(filter.type()));
        }
        if (filter.harvestSeasonId() != null) {
            appendFilter(summary, "Safra filtrada");
        }
        if (filter.categoryId() != null
                || (filter.categoryIds() != null && !filter.categoryIds().isEmpty())) {
            appendFilter(summary, "Categoria filtrada");
        }
        if (filter.description() != null && !filter.description().isBlank()) {
            appendFilter(summary, "Busca: " + filter.description().trim());
        }
        return summary.toString();
    }

    private void appendFilter(StringBuilder summary, String value) {
        if (!summary.isEmpty()) {
            summary.append(" · ");
        }
        summary.append(value);
    }

    private String filename(FinancialTransactionFilterRequest filter, String extension) {
        String start =
                filter.transactionDateStart() == null
                        ? "todas"
                        : filter.transactionDateStart().toString();
        String end =
                filter.transactionDateEnd() == null
                        ? "todas"
                        : filter.transactionDateEnd().toString();
        return "movimentacoes-" + start + "-a-" + end + "." + extension;
    }

    private String safeSpreadsheetText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        char firstCharacter = value.charAt(0);
        return firstCharacter == '='
                        || firstCharacter == '+'
                        || firstCharacter == '-'
                        || firstCharacter == '@'
                ? "'" + value
                : value;
    }

    private String valueOrNotInformed(String value) {
        return value == null || value.isBlank() ? "Não informada" : value;
    }

    private String typeLabel(TransactionType type) {
        return TransactionType.INCOME.equals(type) ? "Receita" : "Despesa";
    }

    private String paymentStatusLabel(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "Pendente";
            case PAID -> "Paga";
            case OVERDUE -> "Atrasada";
            case CANCELED -> "Cancelada";
        };
    }

    private String paymentMethodLabel(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return "Não informada";
        }

        return switch (paymentMethod) {
            case PIX -> "Pix";
            case CASH -> "Dinheiro";
            case CREDIT_CARD -> "Cartão de crédito";
            case DEBIT_CARD -> "Cartão de débito";
            case BANK_TRANSFER -> "Transferência bancária";
            case BOLETO -> "Boleto";
            case CHECK -> "Cheque";
            case OTHER -> "Outro";
        };
    }

    private String formatDate(LocalDate value) {
        return value == null ? "Não informado" : DATE_FORMATTER.format(value);
    }

    private String formatCurrency(BigDecimal value) {
        return java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                .format(value);
    }

    private record ExportData(
            FinancialTransactionFilterRequest filter,
            String farmName,
            List<FinancialTransactionResponse> transactions) {}
}
