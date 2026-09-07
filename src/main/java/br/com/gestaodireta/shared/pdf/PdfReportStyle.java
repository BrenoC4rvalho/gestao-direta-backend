package br.com.gestaodireta.shared.pdf;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

public final class PdfReportStyle {

    public static final java.awt.Color PRIMARY_COLOR = new java.awt.Color(28, 100, 69);

    public static final java.awt.Color MUTED_COLOR = new java.awt.Color(88, 99, 112);

    public static final java.awt.Color BORDER_COLOR = new java.awt.Color(220, 225, 220);

    private PdfReportStyle() {}

    public static PdfPageEventHelper footer(String reportName) {
        return new ReportFooter(reportName);
    }

    private static class ReportFooter extends PdfPageEventHelper {

        private final String reportName;

        private ReportFooter(String reportName) {
            this.reportName = reportName;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            org.openpdf.text.Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED_COLOR);
            Phrase footer =
                    new Phrase(
                            "Gestão Direta · " + reportName + " · Página " + writer.getPageNumber(),
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
