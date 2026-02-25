package org.example.sistema_gestion_vitalexa.service.impl;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.sistema_gestion_vitalexa.dto.PayrollResponse;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.service.PayrollExportService;
import org.example.sistema_gestion_vitalexa.service.PayrollService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollExportServiceImpl implements PayrollExportService {

    private final PayrollService payrollService;

    private static final DeviceRgb COLOR_HEADER     = new DeviceRgb(34, 85, 136);
    private static final DeviceRgb COLOR_SUBHEADER  = new DeviceRgb(52, 120, 190);
    private static final DeviceRgb COLOR_GREEN       = new DeviceRgb(39, 174, 96);
    private static final DeviceRgb COLOR_RED         = new DeviceRgb(192, 57, 43);
    private static final DeviceRgb COLOR_LIGHT_BLUE  = new DeviceRgb(214, 234, 248);
    private static final DeviceRgb COLOR_LIGHT_GREEN = new DeviceRgb(212, 239, 223);
    private static final DeviceRgb COLOR_LIGHT_RED   = new DeviceRgb(250, 219, 216);

    // =========================================================================
    // EXCEL — TODOS LOS VENDEDORES
    // =========================================================================

    @Override
    public byte[] exportAllPayrollsToExcel(int month, int year) {
        List<PayrollResponse> payrolls = payrollService.findByMonthAndYear(month, year);
        if (payrolls.isEmpty()) {
            throw new BusinessExeption("No hay nóminas calculadas para " + getMonthName(month) + " " + year);
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle   = createExcelHeaderStyle(workbook);
            CellStyle dataStyle     = createExcelDataStyle(workbook);
            CellStyle currencyStyle = createExcelCurrencyStyle(workbook);
            CellStyle yesStyle      = createExcelColorStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle noStyle       = createExcelColorStyle(workbook, IndexedColors.ROSE);
            CellStyle titleStyle    = createExcelTitleStyle(workbook);

            // Hoja 1: Resumen general
            createSummarySheet(workbook, payrolls, month, year, headerStyle, dataStyle, currencyStyle, titleStyle);

            // Una hoja por cada vendedor
            for (PayrollResponse p : payrolls) {
                createVendorSheet(workbook, p, headerStyle, dataStyle, currencyStyle, yesStyle, noStyle, titleStyle);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (BusinessExeption e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generando Excel nómina general {}/{}", month, year, e);
            throw new RuntimeException("Error al generar Excel de nómina", e);
        }
    }

    // =========================================================================
    // EXCEL — VENDEDOR ESPECÍFICO
    // =========================================================================

    @Override
    public byte[] exportVendorPayrollToExcel(UUID vendedorId, int month, int year) {
        PayrollResponse payroll = payrollService.findByVendedorAndMonthYear(vendedorId, month, year);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle   = createExcelHeaderStyle(workbook);
            CellStyle dataStyle     = createExcelDataStyle(workbook);
            CellStyle currencyStyle = createExcelCurrencyStyle(workbook);
            CellStyle yesStyle      = createExcelColorStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle noStyle       = createExcelColorStyle(workbook, IndexedColors.ROSE);
            CellStyle titleStyle    = createExcelTitleStyle(workbook);

            createVendorSheet(workbook, payroll, headerStyle, dataStyle, currencyStyle, yesStyle, noStyle, titleStyle);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (BusinessExeption e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generando Excel nómina vendedor {}", vendedorId, e);
            throw new RuntimeException("Error al generar Excel de nómina", e);
        }
    }

    // =========================================================================
    // PDF — TODOS LOS VENDEDORES
    // =========================================================================

    @Override
    public byte[] exportAllPayrollsToPdf(int month, int year) {
        List<PayrollResponse> payrolls = payrollService.findByMonthAndYear(month, year);
        if (payrolls.isEmpty()) {
            throw new BusinessExeption("No hay nóminas calculadas para " + getMonthName(month) + " " + year);
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            addPdfTitle(document, "NÓMINA GENERAL — " + getMonthName(month).toUpperCase() + " " + year);
            addPdfSubtitle(document, payrolls.size() + " vendedor(es)");

            for (PayrollResponse p : payrolls) {
                addPayrollSectionToPdf(document, p);
                document.add(new Paragraph("\n"));
            }

            document.close();
            return baos.toByteArray();
        } catch (BusinessExeption e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generando PDF nómina general {}/{}", month, year, e);
            throw new RuntimeException("Error al generar PDF de nómina", e);
        }
    }

    // =========================================================================
    // PDF — VENDEDOR ESPECÍFICO
    // =========================================================================

    @Override
    public byte[] exportVendorPayrollToPdf(UUID vendedorId, int month, int year) {
        PayrollResponse payroll = payrollService.findByVendedorAndMonthYear(vendedorId, month, year);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            addPdfTitle(document,
                    "NÓMINA — " + payroll.vendedorUsername().toUpperCase()
                    + " — " + getMonthName(month).toUpperCase() + " " + year);
            addPayrollSectionToPdf(document, payroll);

            document.close();
            return baos.toByteArray();
        } catch (BusinessExeption e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generando PDF nómina vendedor {}", vendedorId, e);
            throw new RuntimeException("Error al generar PDF de nómina", e);
        }
    }

    // =========================================================================
    // HELPERS EXCEL
    // =========================================================================

    private void createSummarySheet(Workbook wb, List<PayrollResponse> payrolls,
                                    int month, int year,
                                    CellStyle hStyle, CellStyle dStyle,
                                    CellStyle cStyle, CellStyle tStyle) {
        Sheet sheet = wb.createSheet("Resumen " + getMonthName(month) + " " + year);
        int rowNum = 0;

        // Título
        Row titleRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("NÓMINA " + getMonthName(month).toUpperCase() + " " + year);
        titleCell.setCellStyle(tStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));
        rowNum++;

        // Encabezados
        String[] headers = {
            "Vendedor", "Salario Base",
            "Meta Ventas", "Total Vendido", "¿Cumplió?", "Comisión Ventas",
            "% Recaudo", "¿Cumplió Recaudo?", "Comisión Recaudo",
            "Comisión General", "TOTAL A PAGAR"
        };
        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(hStyle);
        }

        BigDecimal grandTotal = BigDecimal.ZERO;
        for (PayrollResponse p : payrolls) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            setCell(row, col++, p.vendedorUsername(), dStyle);
            setCurrencyCell(row, col++, p.baseSalary(), cStyle);
            setCurrencyCell(row, col++, p.salesGoalTarget() != null ? p.salesGoalTarget() : BigDecimal.ZERO, cStyle);
            setCurrencyCell(row, col++, p.totalSold(), cStyle);
            setCell(row, col++, p.salesGoalMet() ? "✓ Sí" : "✗ No", dStyle);
            setCurrencyCell(row, col++, p.salesCommissionAmount(), cStyle);
            setCell(row, col++, p.collectionPct().setScale(2, RoundingMode.HALF_UP) + "%", dStyle);
            setCell(row, col++, p.collectionGoalMet() ? "✓ Sí" : "✗ No", dStyle);
            setCurrencyCell(row, col++, p.collectionCommissionAmount(), cStyle);
            setCurrencyCell(row, col++, p.generalCommissionAmount(), cStyle);
            setCurrencyCell(row, col++, p.totalPayout(), cStyle);
            grandTotal = grandTotal.add(p.totalPayout());
        }

        // Fila total
        rowNum++;
        Row totalRow = sheet.createRow(rowNum);
        setCell(totalRow, 0, "TOTAL NÓMINA:", hStyle);
        setCurrencyCell(totalRow, 10, grandTotal, cStyle);

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void createVendorSheet(Workbook wb, PayrollResponse p,
                                   CellStyle hStyle, CellStyle dStyle, CellStyle cStyle,
                                   CellStyle yStyle, CellStyle nStyle, CellStyle tStyle) {
        String sheetName = p.vendedorUsername().length() > 31
                ? p.vendedorUsername().substring(0, 31) : p.vendedorUsername();
        Sheet sheet = wb.createSheet(sheetName);
        int rowNum = 0;

        // Título
        Row titleRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("NÓMINA — " + p.vendedorUsername().toUpperCase()
                + " — " + getMonthName(p.month()) + " " + p.year());
        titleCell.setCellStyle(tStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
        rowNum++;

        // ── Salario Base ───────────────────────────────────────────────────
        addSectionHeader(sheet, rowNum++, "SALARIO BASE", hStyle, 3);
        addDetailRow(sheet, rowNum++, "Salario base mensual:", fmt(p.baseSalary()), dStyle, cStyle);
        rowNum++;

        // ── Comisión por Ventas ────────────────────────────────────────────
        addSectionHeader(sheet, rowNum++, "COMISIÓN POR META DE VENTAS", hStyle, 3);
        addDetailRow(sheet, rowNum++, "Meta de ventas:", fmt(p.salesGoalTarget()), dStyle, cStyle);
        addDetailRow(sheet, rowNum++, "Total vendido:", fmt(p.totalSold()), dStyle, cStyle);
        addDetailRow(sheet, rowNum++, "¿Cumplió meta?:", p.salesGoalMet() ? "✓ SÍ" : "✗ NO",
                dStyle, p.salesGoalMet() ? yStyle : nStyle);
        addDetailRow(sheet, rowNum++, "% Comisión ventas:",
                pct(p.salesCommissionPct()), dStyle, dStyle);
        addDetailRow(sheet, rowNum++, "Comisión por ventas:", fmt(p.salesCommissionAmount()), dStyle, cStyle);
        rowNum++;

        // ── Comisión por Recaudo ───────────────────────────────────────────
        addSectionHeader(sheet, rowNum++, "COMISIÓN POR META DE RECAUDO", hStyle, 3);
        addDetailRow(sheet, rowNum++, "Vendido mes anterior:", fmt(p.prevMonthTotalSold()), dStyle, cStyle);
        addDetailRow(sheet, rowNum++, "Total recaudado este mes:", fmt(p.totalCollected()), dStyle, cStyle);
        addDetailRow(sheet, rowNum++, "% Recaudado:",
                p.collectionPct().setScale(2, RoundingMode.HALF_UP) + "%", dStyle, dStyle);
        addDetailRow(sheet, rowNum++, "Umbral requerido:",
                pct(p.collectionCommissionPct().equals(new BigDecimal("0.0300"))
                        ? new BigDecimal("0.8000") : new BigDecimal("0.8000")),
                dStyle, dStyle);
        addDetailRow(sheet, rowNum++, "¿Cumplió recaudo?:", p.collectionGoalMet() ? "✓ SÍ" : "✗ NO",
                dStyle, p.collectionGoalMet() ? yStyle : nStyle);
        addDetailRow(sheet, rowNum++, "% Comisión recaudo:",
                pct(p.collectionCommissionPct()), dStyle, dStyle);
        addDetailRow(sheet, rowNum++, "Comisión por recaudo:", fmt(p.collectionCommissionAmount()), dStyle, cStyle);
        rowNum++;

        // ── Comisión General ───────────────────────────────────────────────
        addSectionHeader(sheet, rowNum++, "COMISIÓN GENERAL POR METAS GLOBALES", hStyle, 3);
        addDetailRow(sheet, rowNum++, "¿Habilitada?:", p.generalCommissionEnabled() ? "✓ SÍ" : "✗ NO",
                dStyle, p.generalCommissionEnabled() ? yStyle : nStyle);
        addDetailRow(sheet, rowNum++, "Suma total de metas globales:", fmt(p.totalGlobalGoals()), dStyle, cStyle);
        addDetailRow(sheet, rowNum++, "% Comisión general:", pct(p.generalCommissionPct()), dStyle, dStyle);
        addDetailRow(sheet, rowNum++, "Comisión general:", fmt(p.generalCommissionAmount()), dStyle, cStyle);
        rowNum++;

        // ── Totales ────────────────────────────────────────────────────────
        addSectionHeader(sheet, rowNum++, "RESUMEN FINAL", hStyle, 3);
        addDetailRow(sheet, rowNum++, "Salario base:", fmt(p.baseSalary()), dStyle, cStyle);
        addDetailRow(sheet, rowNum++, "Total comisiones:", fmt(p.totalCommissions()), dStyle, cStyle);

        Row totalRow = sheet.createRow(rowNum);
        org.apache.poi.ss.usermodel.Cell lbl = totalRow.createCell(0);
        lbl.setCellValue("TOTAL A PAGAR:");
        lbl.setCellStyle(hStyle);
        org.apache.poi.ss.usermodel.Cell val = totalRow.createCell(2);
        val.setCellValue(p.totalPayout().doubleValue());
        val.setCellStyle(cStyle);

        if (p.notes() != null && !p.notes().isBlank()) {
            rowNum += 2;
            Row notesRow = sheet.createRow(rowNum);
            setCell(notesRow, 0, "Notas: " + p.notes(), dStyle);
        }

        sheet.setColumnWidth(0, 9000);
        sheet.setColumnWidth(1, 500);
        sheet.autoSizeColumn(2);
    }

    // =========================================================================
    // HELPERS PDF
    // =========================================================================

    private void addPdfTitle(Document doc, String text) {
        doc.add(new Paragraph(text)
                .setFontSize(16).setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));
    }

    private void addPdfSubtitle(Document doc, String text) {
        doc.add(new Paragraph(text)
                .setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(16));
    }

    private void addPayrollSectionToPdf(Document doc, PayrollResponse p) {
        // Nombre vendedor
        doc.add(new Paragraph("Vendedor: " + p.vendedorUsername())
                .setFontSize(13).setBold()
                .setBackgroundColor(COLOR_HEADER)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(6)
                .setMarginTop(8).setMarginBottom(4));

        Table table = new Table(UnitValue.createPercentArray(new float[]{3f, 2f}))
                .useAllAvailableWidth()
                .setMarginBottom(6);

        // ── Salario Base ──────────────────────────────────────────────────
        addPdfSectionRow(table, "SALARIO BASE", true);
        addPdfDataRow(table, "Salario base mensual", fmtPdf(p.baseSalary()), false);

        // ── Comisión Ventas ───────────────────────────────────────────────
        addPdfSectionRow(table, "COMISIÓN POR META DE VENTAS", true);
        addPdfDataRow(table, "Meta de ventas", fmtPdf(p.salesGoalTarget()), false);
        addPdfDataRow(table, "Total vendido", fmtPdf(p.totalSold()), false);
        addPdfDataRow(table, "¿Cumplió meta?", p.salesGoalMet() ? "✓ SÍ" : "✗ NO",
                !p.salesGoalMet());
        addPdfDataRow(table, "% Comisión", pctPdf(p.salesCommissionPct()), false);
        addPdfDataRow(table, "Comisión por ventas", fmtPdf(p.salesCommissionAmount()), false);

        // ── Comisión Recaudo ──────────────────────────────────────────────
        addPdfSectionRow(table, "COMISIÓN POR META DE RECAUDO", true);
        addPdfDataRow(table, "Vendido mes anterior", fmtPdf(p.prevMonthTotalSold()), false);
        addPdfDataRow(table, "Total recaudado", fmtPdf(p.totalCollected()), false);
        addPdfDataRow(table, "% Recaudado",
                p.collectionPct().setScale(2, RoundingMode.HALF_UP) + "%", false);
        addPdfDataRow(table, "Umbral requerido (80%)", "80.00%", false);
        addPdfDataRow(table, "¿Cumplió recaudo?", p.collectionGoalMet() ? "✓ SÍ" : "✗ NO",
                !p.collectionGoalMet());
        addPdfDataRow(table, "% Comisión recaudo", pctPdf(p.collectionCommissionPct()), false);
        addPdfDataRow(table, "Comisión por recaudo", fmtPdf(p.collectionCommissionAmount()), false);

        // ── Comisión General ──────────────────────────────────────────────
        addPdfSectionRow(table, "COMISIÓN GENERAL POR METAS GLOBALES", true);
        addPdfDataRow(table, "¿Habilitada?", p.generalCommissionEnabled() ? "✓ SÍ" : "✗ NO",
                !p.generalCommissionEnabled());
        addPdfDataRow(table, "Suma de metas globales", fmtPdf(p.totalGlobalGoals()), false);
        addPdfDataRow(table, "% Comisión general", pctPdf(p.generalCommissionPct()), false);
        addPdfDataRow(table, "Comisión general", fmtPdf(p.generalCommissionAmount()), false);

        // ── Totales ───────────────────────────────────────────────────────
        addPdfSectionRow(table, "RESUMEN FINAL", true);
        addPdfDataRow(table, "Salario base", fmtPdf(p.baseSalary()), false);
        addPdfDataRow(table, "Total comisiones", fmtPdf(p.totalCommissions()), false);

        // Fila total destacada
        Cell totalLabel = new Cell()
                .add(new Paragraph("TOTAL A PAGAR").setBold())
                .setBackgroundColor(COLOR_GREEN).setFontColor(ColorConstants.WHITE)
                .setPadding(6);
        Cell totalValue = new Cell()
                .add(new Paragraph(fmtPdf(p.totalPayout())).setBold())
                .setBackgroundColor(COLOR_GREEN).setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.RIGHT).setPadding(6);
        table.addCell(totalLabel);
        table.addCell(totalValue);

        doc.add(table);

        if (p.notes() != null && !p.notes().isBlank()) {
            doc.add(new Paragraph("Notas: " + p.notes()).setFontSize(9).setItalic());
        }
    }

    private void addPdfSectionRow(Table table, String title, boolean isSection) {
        Cell cell = new Cell(1, 2)
                .add(new Paragraph(title).setBold().setFontSize(10))
                .setBackgroundColor(COLOR_SUBHEADER)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(5);
        table.addCell(cell);
    }

    private void addPdfDataRow(Table table, String label, String value, boolean highlight) {
        DeviceRgb bg = highlight ? COLOR_LIGHT_RED : COLOR_LIGHT_BLUE;
        table.addCell(new Cell().add(new Paragraph(label).setFontSize(9))
                .setBackgroundColor(bg).setPadding(4));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(9))
                .setBackgroundColor(bg).setTextAlignment(TextAlignment.RIGHT).setPadding(4));
    }

    // =========================================================================
    // HELPERS COMUNES EXCEL
    // =========================================================================

    private void addSectionHeader(Sheet sheet, int rowNum, String title, CellStyle style, int cols) {
        Row row = sheet.createRow(rowNum);
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        if (cols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, cols - 1));
        }
    }

    private void addDetailRow(Sheet sheet, int rowNum, String label, String value,
                               CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        org.apache.poi.ss.usermodel.Cell lbl = row.createCell(0);
        lbl.setCellValue(label);
        lbl.setCellStyle(labelStyle);
        org.apache.poi.ss.usermodel.Cell val = row.createCell(2);
        val.setCellValue(value);
        val.setCellStyle(valueStyle);
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setCurrencyCell(Row row, int col, BigDecimal value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : 0.0);
        cell.setCellStyle(style);
    }

    private CellStyle createExcelHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createExcelTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createExcelDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createExcelCurrencyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createExcelColorStyle(Workbook wb, IndexedColors color) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // =========================================================================
    // FORMATTERS
    // =========================================================================

    private String fmt(BigDecimal v) {
        if (v == null) return "$0.00";
        return "$" + String.format("%,.2f", v);
    }

    private String fmtPdf(BigDecimal v) {
        if (v == null) return "$0.00";
        return "$" + String.format("%,.2f", v);
    }

    private String pct(BigDecimal v) {
        if (v == null) return "0.00%";
        return v.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private String pctPdf(BigDecimal v) {
        return pct(v);
    }

    private String getMonthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, new Locale("es", "CO"));
    }
}

