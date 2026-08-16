package com.turbotikects.turbotikectsserver.services;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.turbotikects.turbotikectsserver.dto.ReportSummaryDto;
import com.turbotikects.turbotikectsserver.dto.ReportTipDto;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * FEAT-05.5 — CSV (Apache Commons CSV) and PDF (OpenPDF) writers, including the "no data found"
 * empty-state variant of each. Reuses the app's existing FileStorageService (already used for
 * ticket attachments, path-traversal-safe) rather than inventing a new storage mechanism — see
 * V2/repoets/feat-05-05-export-engine.html.
 */
@Service
public class ReportExportService {

    private static final String NO_DATA_MESSAGE =
            "No data was found matching this report's criteria for the selected period.";

    private final FileStorageService fileStorageService;

    public ReportExportService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public String writeCsv(Long runId, List<String> selectedFields, List<Map<String, Object>> rows) throws Exception {
        StringWriter sw = new StringWriter();
        if (rows == null || rows.isEmpty()) {
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("Message").build();
            try (CSVPrinter printer = new CSVPrinter(sw, format)) {
                printer.printRecord(NO_DATA_MESSAGE);
            }
        } else {
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(selectedFields.toArray(new String[0])).build();
            try (CSVPrinter printer = new CSVPrinter(sw, format)) {
                for (Map<String, Object> row : rows) {
                    Object[] values = selectedFields.stream().map(f -> stringify(row.get(f))).toArray();
                    printer.printRecord(values);
                }
            }
        }
        return persist(runId, "csv", sw.toString().getBytes(StandardCharsets.UTF_8));
    }

    public String writePdf(Long runId, String reportName, List<String> selectedFields,
                            Map<String, String> fieldLabels, List<Map<String, Object>> rows,
                            ReportSummaryDto summary, String criteriaRecap) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font metaFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
        document.add(new Paragraph(reportName, titleFont));
        document.add(new Paragraph("Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")), metaFont));
        document.add(new Paragraph(" "));

        if (rows == null || rows.isEmpty()) {
            Font msgFont = new Font(Font.HELVETICA, 13, Font.BOLD);
            Paragraph title = new Paragraph("No data was found", msgFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(40f);
            document.add(title);

            Font subFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);
            Paragraph sub = new Paragraph(NO_DATA_MESSAGE
                    + (criteriaRecap != null && !criteriaRecap.isBlank() ? "\nCriteria: " + criteriaRecap : ""), subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingBefore(8f);
            document.add(sub);
        } else {
            if (summary != null && summary.getSummary() != null) {
                Font sumHeaderFont = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(59, 130, 246));
                Font bodyFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
                document.add(new Paragraph("AI Summary", sumHeaderFont));
                document.add(new Paragraph(summary.getSummary(), bodyFont));
                if (summary.getTips() != null && !summary.getTips().isEmpty()) {
                    document.add(new Paragraph(" "));
                    document.add(new Paragraph("Tips for Improvement", sumHeaderFont));
                    for (ReportTipDto tip : summary.getTips()) {
                        document.add(new Paragraph(
                                "• " + tip.getDescription() + "  (" + tip.getConfidencePercent() + "% confidence)",
                                bodyFont));
                    }
                }
                document.add(new Paragraph(" "));
            }

            PdfPTable table = new PdfPTable(Math.max(selectedFields.size(), 1));
            table.setWidthPercentage(100);
            Font headFont = new Font(Font.HELVETICA, 8, Font.BOLD);
            Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL);
            for (String field : selectedFields) {
                PdfPCell cell = new PdfPCell(new Phrase(fieldLabels.getOrDefault(field, field), headFont));
                cell.setBackgroundColor(new Color(238, 238, 238));
                cell.setPadding(4f);
                table.addCell(cell);
            }
            for (Map<String, Object> row : rows) {
                for (String field : selectedFields) {
                    PdfPCell cell = new PdfPCell(new Phrase(plainText(row.get(field)), cellFont));
                    cell.setPadding(4f);
                    table.addCell(cell);
                }
            }
            document.add(table);

            Font footFont = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);
            Paragraph footer = new Paragraph("report_runs #" + runId + " · " + rows.size() + " rows", footFont);
            footer.setSpacingBefore(8f);
            document.add(footer);
        }

        document.close();
        return persist(runId, "pdf", baos.toByteArray());
    }

    private String persist(Long runId, String extension, byte[] bytes) throws Exception {
        String relativePath = "reports/" + runId + "." + extension;
        fileStorageService.store(new ByteArrayInputStream(bytes), relativePath, bytes.length);
        return relativePath;
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** PDF-only: ticket description and any rich-text-authored custom field (this app's
     * RichTextEditor/TinyMCE fields) are stored as HTML — rendering raw markup as literal text
     * ("&lt;p&gt;...&lt;/p&gt;") reads as broken. Jsoup.parse(...).text() extracts clean plain
     * text, same library already used for KB article sanitization elsewhere in this codebase.
     * CSV/the on-screen preview keep the raw value untouched (not requested, and CSV consumers
     * may want the original markup for round-tripping). A plain-text value with no real tags is
     * unaffected — Jsoup only strips things that actually parse as markup. */
    private static String plainText(Object value) {
        String raw = stringify(value);
        if (raw.isEmpty() || raw.indexOf('<') < 0) return raw;
        return Jsoup.parse(raw).text();
    }
}
