package org.example.sistema_gestion_vitalexa.service.impl;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.service.ReportExportService;
import org.example.sistema_gestion_vitalexa.service.ReportService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExportServiceImpl implements ReportExportService {

    private final ReportService reportService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // =============================================
    // EXPORTAR REPORTE COMPLETO A PDF
    // =============================================
    @Override
    public byte[] exportReportToPdf(ReportDTO report, LocalDate startDate, LocalDate endDate) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // HEADER
            addPdfHeader(document, "REPORTE GENERAL DE GESTIÓN", startDate, endDate);

            // SECCIÓN 1: VENTAS
            addSalesSection(document, report.salesReport());

            // SECCIÓN 2: PRODUCTOS
            addProductSection(document, report.productReport());

            // SECCIÓN 3: VENDEDORES
            addVendorSection(document, report.vendorReport());

            // SECCIÓN 4: CLIENTES
            addClientSection(document, report.clientReport());

            // FOOTER
            addPdfFooter(document);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF completo", e);
            throw new RuntimeException("Error al generar reporte PDF", e);
        }
    }

    // =============================================
    // EXPORTAR REPORTE DE VENTAS A PDF
    // =============================================
    @Override
    public byte[] exportSalesReportToPdf(LocalDate startDate, LocalDate endDate) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SalesReportDTO salesReport = reportService.getSalesReport(startDate, endDate);

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            addPdfHeader(document, "REPORTE DE VENTAS", startDate, endDate);
            addSalesSection(document, salesReport);
            addPdfFooter(document);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF de ventas", e);
            throw new RuntimeException("Error al generar reporte de ventas PDF", e);
        }
    }

    // =============================================
    // EXPORTAR REPORTE COMPLETO A EXCEL
    // =============================================
    @Override
    public byte[] exportReportToExcel(ReportDTO report, LocalDate startDate, LocalDate endDate) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Crear estilos
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);

            // HOJA 1: RESUMEN
            createSummarySheet(workbook, report, startDate, endDate, headerStyle, dataStyle, currencyStyle);

            // HOJA 2: VENTAS DIARIAS
            createDailySalesSheet(workbook, report.salesReport(), headerStyle, dataStyle, currencyStyle);

            // HOJA 3: PRODUCTOS TOP
            createTopProductsSheet(workbook, report.productReport(), headerStyle, dataStyle, currencyStyle);

            // HOJA 4: VENDEDORES
            createVendorsSheet(workbook, report.vendorReport(), headerStyle, dataStyle, currencyStyle);

            // HOJA 5: CLIENTES TOP
            createTopClientsSheet(workbook, report.clientReport(), headerStyle, dataStyle, currencyStyle);

            List<VendorDailySalesDTO> vendorSalesReports =
                    reportService.getVendorDailySalesReport(startDate, endDate);
            createVendorDailySalesSheets(workbook, vendorSalesReports, headerStyle, dataStyle, currencyStyle);

            workbook.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando Excel completo", e);
            throw new RuntimeException("Error al generar reporte Excel", e);
        }
    }

    // =============================================
    // EXPORTAR PRODUCTOS A EXCEL
    // =============================================
    @Override
    public byte[] exportProductReportToExcel() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            ProductReportDTO productReport = reportService.getProductReport();

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);

            createTopProductsSheet(workbook, productReport, headerStyle, dataStyle, currencyStyle);
            createLowStockSheet(workbook, productReport, headerStyle, dataStyle);

            workbook.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando Excel de productos", e);
            throw new RuntimeException("Error al generar reporte de productos Excel", e);
        }
    }

    // =============================================
    // EXPORTAR REPORTE A CSV
    // =============================================
    @Override
    public byte[] exportReportToCsv(ReportDTO report, LocalDate startDate, LocalDate endDate) {
        try (StringWriter sw = new StringWriter();
             CSVWriter csvWriter = new CSVWriter(sw)) {

            // HEADER
            csvWriter.writeNext(new String[]{"REPORTE GENERAL DE GESTIÓN - VITALEXA"});
            csvWriter.writeNext(new String[]{"Período: " + startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER)});
            csvWriter.writeNext(new String[]{""}); // línea en blanco
            // VENTAS
            csvWriter.writeNext(new String[]{"===== RESUMEN DE VENTAS ====="});
            csvWriter.writeNext(new String[]{"Métrica", "Valor"});
            csvWriter.writeNext(new String[]{"Ingresos Totales", "$" + report.salesReport().totalRevenue()});
            csvWriter.writeNext(new String[]{"Total Órdenes", String.valueOf(report.salesReport().totalOrders())});
            csvWriter.writeNext(new String[]{"Órdenes Completadas", String.valueOf(report.salesReport().completedOrders())});
            csvWriter.writeNext(new String[]{"Valor Promedio", "$" + report.salesReport().averageOrderValue()});
            csvWriter.writeNext(new String[]{""});

            // PRODUCTOS
            csvWriter.writeNext(new String[]{"===== INVENTARIO DE PRODUCTOS ====="});
            csvWriter.writeNext(new String[]{"Total Productos", String.valueOf(report.productReport().totalProducts())});
            csvWriter.writeNext(new String[]{"Productos Activos", String.valueOf(report.productReport().activeProducts())});
            csvWriter.writeNext(new String[]{"Valor Inventario", "$" + report.productReport().totalInventoryValue()});
            csvWriter.writeNext(new String[]{""});

            // TOP PRODUCTOS
            csvWriter.writeNext(new String[]{"Producto", "Cantidad Vendida", "Ingresos"});
            report.productReport().topSellingProducts().forEach(p ->
                    csvWriter.writeNext(new String[]{p.productName(), String.valueOf(p.quantitySold()), "$" + p.revenue()})
            );
            csvWriter.writeNext(new String[]{""});

            // VENDEDORES
            csvWriter.writeNext(new String[]{"===== TOP VENDEDORES ====="});
            csvWriter.writeNext(new String[]{"Vendedor", "Órdenes", "Ingresos", "Promedio por Orden"});
            report.vendorReport().topVendors().forEach(v ->
                    csvWriter.writeNext(new String[]{
                            v.vendorName(),
                            String.valueOf(v.totalOrders()),
                            "$" + v.totalRevenue(),
                            "$" + v.averageOrderValue()
                    })
            );
            csvWriter.writeNext(new String[]{""});

            // CLIENTES
            csvWriter.writeNext(new String[]{"===== TOP CLIENTES ====="});
            csvWriter.writeNext(new String[]{"Cliente", "Teléfono", "Total Compras", "Órdenes"});
            report.clientReport().topClients().forEach(c ->
                    csvWriter.writeNext(new String[]{
                            c.clientName(),
                            c.clientPhone() != null ? c.clientPhone() : "N/A",
                            "$" + c.totalSpent(),
                            String.valueOf(c.totalOrders())
                    })
            );

            String csvContent = sw.toString();
            // ✅ Agregar BOM UTF-8 para que Excel lo reconozca
            byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);

            byte[] result = new byte[bom.length + csvBytes.length];
            System.arraycopy(bom, 0, result, 0, bom.length);
            System.arraycopy(csvBytes, 0, result, bom.length, csvBytes.length);

            return result;



        } catch (Exception e) {
            log.error("Error generando CSV", e);
            throw new RuntimeException("Error al generar reporte CSV", e);
        }
    }

    // =============================================
    // EXPORTAR CLIENTES A CSV
    // =============================================
    @Override
    public byte[] exportClientReportToCsv() {
        try (StringWriter sw = new StringWriter();
             CSVWriter csvWriter = new CSVWriter(sw)) {

            ClientReportDTO clientReport = reportService.getClientReport();

            csvWriter.writeNext(new String[]{"REPORTE DE CLIENTES - VITALEXA"});
            csvWriter.writeNext(new String[]{""});
            csvWriter.writeNext(new String[]{"Total Clientes", String.valueOf(clientReport.totalClients())});
            csvWriter.writeNext(new String[]{"Clientes Activos", String.valueOf(clientReport.activeClients())});
            csvWriter.writeNext(new String[]{""});
            csvWriter.writeNext(new String[]{"Cliente", "Teléfono", "Total Compras", "Número de Órdenes"});

            clientReport.topClients().forEach(c ->
                    csvWriter.writeNext(new String[]{
                            c.clientName(),
                            c.clientPhone() != null ? c.clientPhone() : "N/A",
                            "$" + c.totalSpent(),
                            String.valueOf(c.totalOrders())
                    })
            );

            return sw.toString().getBytes();

        } catch (Exception e) {
            log.error("Error generando CSV de clientes", e);
            throw new RuntimeException("Error al generar CSV de clientes", e);
        }
    }

    // =============================================
    // MÉTODOS AUXILIARES PARA PDF
    // =============================================

    private void addPdfHeader(Document document, String title, LocalDate startDate, LocalDate endDate) {
        Paragraph titlePara = new Paragraph(title)
                .setFontSize(20)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10);
        document.add(titlePara);

        Paragraph subtitle = new Paragraph(
                "Período: " + startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER)
        ).setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(subtitle);
    }

    private void addPdfFooter(Document document) {
        Paragraph footer = new Paragraph("Generado: " + LocalDate.now().format(DATE_FORMATTER))
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20);
        document.add(footer);
    }

    private void addSalesSection(Document document, SalesReportDTO sales) {
        Paragraph sectionTitle = new Paragraph("RESUMEN DE VENTAS")
                .setFontSize(14)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10);
        document.add(sectionTitle);

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth();

        addPdfTableCell(table, "Ingresos Totales:", true);
        addPdfTableCell(table, "$" + sales.totalRevenue(), false);

        addPdfTableCell(table, "Total Órdenes:", true);
        addPdfTableCell(table, String.valueOf(sales.totalOrders()), false);

        addPdfTableCell(table, "Órdenes Completadas:", true);
        addPdfTableCell(table, String.valueOf(sales.completedOrders()), false);

        addPdfTableCell(table, "Órdenes Pendientes:", true);
        addPdfTableCell(table, String.valueOf(sales.pendingOrders()), false);

        addPdfTableCell(table, "Órdenes Canceladas:", true);
        addPdfTableCell(table, String.valueOf(sales.canceledOrders()), false);

        addPdfTableCell(table, "Valor Promedio por Orden:", true);
        addPdfTableCell(table, "$" + sales.averageOrderValue(), false);

        document.add(table);
    }

    private void addProductSection(Document document, ProductReportDTO products) {
        Paragraph sectionTitle = new Paragraph("INVENTARIO DE PRODUCTOS")
                .setFontSize(14)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10);
        document.add(sectionTitle);

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth();

        addPdfTableCell(table, "Total Productos:", true);
        addPdfTableCell(table, String.valueOf(products.totalProducts()), false);

        addPdfTableCell(table, "Productos Activos:", true);
        addPdfTableCell(table, String.valueOf(products.activeProducts()), false);

        addPdfTableCell(table, "Productos con Stock Bajo:", true);
        addPdfTableCell(table, String.valueOf(products.lowStockProducts()), false);

        addPdfTableCell(table, "Valor Total del Inventario:", true);
        addPdfTableCell(table, "$" + products.totalInventoryValue(), false);

        document.add(table);

        // Top productos
        if (!products.topSellingProducts().isEmpty()) {
            Paragraph topTitle = new Paragraph("Top Productos Más Vendidos")
                    .setFontSize(12)
                    .setBold()
                    .setMarginTop(10);
            document.add(topTitle);

            Table topTable = new Table(UnitValue.createPercentArray(new float[]{3, 1, 2}))
                    .useAllAvailableWidth();

            addPdfTableCell(topTable, "Producto", true);
            addPdfTableCell(topTable, "Cantidad", true);
            addPdfTableCell(topTable, "Ingresos", true);

            products.topSellingProducts().stream().limit(5).forEach(p -> {
                addPdfTableCell(topTable, p.productName(), false);
                addPdfTableCell(topTable, String.valueOf(p.quantitySold()), false);
                addPdfTableCell(topTable, "$" + p.revenue(), false);
            });

            document.add(topTable);
        }
    }

    private void addVendorSection(Document document, VendorReportDTO vendors) {
        Paragraph sectionTitle = new Paragraph("TOP VENDEDORES")
                .setFontSize(14)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10);
        document.add(sectionTitle);

        if (vendors.topVendors().isEmpty()) {
            document.add(new Paragraph("No hay datos de vendedores para este período"));
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1, 2, 2}))
                .useAllAvailableWidth();

        addPdfTableCell(table, "Vendedor", true);
        addPdfTableCell(table, "Órdenes", true);
        addPdfTableCell(table, "Ingresos", true);
        addPdfTableCell(table, "Promedio", true);

        vendors.topVendors().forEach(v -> {
            addPdfTableCell(table, v.vendorName(), false);
            addPdfTableCell(table, String.valueOf(v.totalOrders()), false);
            addPdfTableCell(table, "$" + v.totalRevenue(), false);
            addPdfTableCell(table, "$" + v.averageOrderValue(), false);
        });

        document.add(table);
    }

    private void addClientSection(Document document, ClientReportDTO clients) {
        Paragraph sectionTitle = new Paragraph("TOP CLIENTES")
                .setFontSize(14)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10);
        document.add(sectionTitle);

        Table summary = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth();

        addPdfTableCell(summary, "Total Clientes:", true);
        addPdfTableCell(summary, String.valueOf(clients.totalClients()), false);

        addPdfTableCell(summary, "Clientes Activos:", true);
        addPdfTableCell(summary, String.valueOf(clients.activeClients()), false);

        document.add(summary);

        if (!clients.topClients().isEmpty()) {
            Table topTable = new Table(UnitValue.createPercentArray(new float[]{3, 2, 2, 1}))
                    .useAllAvailableWidth()
                    .setMarginTop(10);

            addPdfTableCell(topTable, "Cliente", true);
            addPdfTableCell(topTable, "Teléfono", true);
            addPdfTableCell(topTable, "Total Compras", true);
            addPdfTableCell(topTable, "Órdenes", true);

            clients.topClients().stream().limit(10).forEach(c -> {
                addPdfTableCell(topTable, c.clientName(), false);
                addPdfTableCell(topTable, c.clientPhone() != null ? c.clientPhone() : "N/A", false);
                addPdfTableCell(topTable, "$" + c.totalSpent(), false);
                addPdfTableCell(topTable, String.valueOf(c.totalOrders()), false);
            });

            document.add(topTable);
        }
    }

    private void addPdfTableCell(Table table, String content, boolean isHeader) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(content).setFontSize(10));

        if (isHeader) {
            cell.setBackgroundColor(new DeviceRgb(52, 73, 94))
                    .setFontColor(ColorConstants.WHITE)
                    .setBold();
        }
        cell.setPadding(5);
        table.addCell(cell);
    }

    // =============================================
    // MÉTODOS AUXILIARES PARA EXCEL
    // =============================================

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
        return style;
    }

    private void createSummarySheet(Workbook workbook, ReportDTO report,
                                    LocalDate startDate, LocalDate endDate,
                                    CellStyle headerStyle, CellStyle dataStyle,
                                    CellStyle currencyStyle) {
        Sheet sheet = workbook.createSheet("Resumen General");

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("REPORTE GENERAL - VITALEXA");
        titleCell.setCellStyle(headerStyle);

        Row periodRow = sheet.createRow(rowNum++);
        periodRow.createCell(0).setCellValue("Período:");
        periodRow.createCell(1).setCellValue(startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER));
        rowNum++;

        Row salesHeader = sheet.createRow(rowNum++);
        salesHeader.createCell(0).setCellValue("VENTAS");
        salesHeader.getCell(0).setCellStyle(headerStyle);

        addExcelSummaryRow(sheet, rowNum++, "Ingresos Totales", report.salesReport().totalRevenue(), dataStyle, currencyStyle);
        addExcelSummaryRow(sheet, rowNum++, "Total Órdenes", report.salesReport().totalOrders(), dataStyle);
        addExcelSummaryRow(sheet, rowNum++, "Órdenes Completadas", report.salesReport().completedOrders(), dataStyle);
        addExcelSummaryRow(sheet, rowNum++, "Valor Promedio", report.salesReport().averageOrderValue(), dataStyle, currencyStyle);
        rowNum++;

        Row productHeader = sheet.createRow(rowNum++);
        productHeader.createCell(0).setCellValue("PRODUCTOS");
        productHeader.getCell(0).setCellStyle(headerStyle);

        addExcelSummaryRow(sheet, rowNum++, "Total Productos", report.productReport().totalProducts(), dataStyle);
        addExcelSummaryRow(sheet, rowNum++, "Productos Activos", report.productReport().activeProducts(), dataStyle);
        addExcelSummaryRow(sheet, rowNum++, "Valor Inventario", report.productReport().totalInventoryValue(), dataStyle, currencyStyle);

        for (int i = 0; i < 2; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void addExcelSummaryRow(Sheet sheet, int rowNum, String label, Object value,
                                    CellStyle dataStyle, CellStyle... valueStyle) {
        Row row = sheet.createRow(rowNum);
        org.apache.poi.ss.usermodel.Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(dataStyle);

        org.apache.poi.ss.usermodel.Cell valueCell = row.createCell(1);
        if (value instanceof BigDecimal) {
            valueCell.setCellValue(((BigDecimal) value).doubleValue());
            if (valueStyle.length > 1) {
                valueCell.setCellStyle(valueStyle[1]);
            }
        } else if (value instanceof Integer) {
            valueCell.setCellValue((Integer) value);
            valueCell.setCellStyle(dataStyle);
        }
    }

    private void createDailySalesSheet(Workbook workbook, SalesReportDTO salesReport,
                                       CellStyle headerStyle, CellStyle dataStyle,
                                       CellStyle currencyStyle) {
        Sheet sheet = workbook.createSheet("Ventas Diarias");

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Fecha", "Ingresos", "Órdenes"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (DailySalesDTO daily : salesReport.dailySales()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(daily.date().format(DATE_FORMATTER));
            org.apache.poi.ss.usermodel.Cell revenueCell = row.createCell(1);
            revenueCell.setCellValue(daily.revenue().doubleValue());
            revenueCell.setCellStyle(currencyStyle);
            row.createCell(2).setCellValue(daily.orders());
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createTopProductsSheet(Workbook workbook, ProductReportDTO productReport,
                                        CellStyle headerStyle, CellStyle dataStyle,
                                        CellStyle currencyStyle) {
        Sheet sheet = workbook.createSheet("Top Productos");

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Producto", "Cantidad Vendida", "Ingresos"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (TopProductDTO product : productReport.topSellingProducts()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(product.productName());
            row.createCell(1).setCellValue(product.quantitySold());
            org.apache.poi.ss.usermodel.Cell revenueCell = row.createCell(2);
            revenueCell.setCellValue(product.revenue().doubleValue());
            revenueCell.setCellStyle(currencyStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createLowStockSheet(Workbook workbook, ProductReportDTO productReport,
                                     CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("Stock Bajo");

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Producto", "Stock Actual", "Estado"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (LowStockProductDTO product : productReport.lowStockDetails()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(product.productName());
            row.createCell(1).setCellValue(product.currentStock());
            row.createCell(2).setCellValue(product.status());
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createVendorsSheet(Workbook workbook, VendorReportDTO vendorReport,
                                    CellStyle headerStyle, CellStyle dataStyle,
                                    CellStyle currencyStyle) {
        Sheet sheet = workbook.createSheet("Vendedores");

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Vendedor", "Órdenes", "Ingresos", "Promedio"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (VendorPerformanceDTO vendor : vendorReport.topVendors()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(vendor.vendorName());
            row.createCell(1).setCellValue(vendor.totalOrders());
            org.apache.poi.ss.usermodel.Cell revenueCell = row.createCell(2);
            revenueCell.setCellValue(vendor.totalRevenue().doubleValue());
            revenueCell.setCellStyle(currencyStyle);
            org.apache.poi.ss.usermodel.Cell avgCell = row.createCell(3);
            avgCell.setCellValue(vendor.averageOrderValue().doubleValue());
            avgCell.setCellStyle(currencyStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createTopClientsSheet(Workbook workbook, ClientReportDTO clientReport,
                                       CellStyle headerStyle, CellStyle dataStyle,
                                       CellStyle currencyStyle) {
        Sheet sheet = workbook.createSheet("Top Clientes");

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Cliente", "Teléfono", "Total Compras", "Órdenes"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (TopClientDTO client : clientReport.topClients()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(client.clientName());
            row.createCell(1).setCellValue(client.clientPhone() != null ? client.clientPhone() : "N/A");
            org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(2);
            totalCell.setCellValue(client.totalSpent().doubleValue());
            totalCell.setCellStyle(currencyStyle);
            row.createCell(3).setCellValue(client.totalOrders());
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Crea una hoja separada para cada vendedora mostrando sus ventas diarias
     * Columnas: Fecha | # Factura | # Cliente | Valor | Total Día
     */
    public void createVendorDailySalesSheets(Workbook workbook,
                                             List<VendorDailySalesDTO> vendorSalesReports,
                                             CellStyle headerStyle, CellStyle dataStyle, CellStyle currencyStyle) {

        for (VendorDailySalesDTO vendor : vendorSalesReports) {
            // Usar WorkbookUtil para sanitizar el nombre de la hoja (máx 31 caracteres)
            String sheetName = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(vendor.vendedorName());
            Sheet sheet = workbook.createSheet(sheetName);

            int rowNum = 0;

            // Título
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("VENTAS DIARIAS - " + vendor.vendedorName().toUpperCase());
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                    titleRow.getRowNum(), titleRow.getRowNum(), 0, 4
            ));

            // Período
            Row periodRow = sheet.createRow(rowNum++);
            periodRow.createCell(0).setCellValue("Período:");
            periodRow.createCell(1).setCellValue(
                    vendor.startDate().format(DATE_FORMATTER) + " a " +
                            vendor.endDate().format(DATE_FORMATTER)
            );
            rowNum++;

            // Encabezados
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Fecha", "# Factura", "# Cliente", "Valor", "Total Día"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos: iterar por cada día
            for (VendorDailyGroupDTO dailyGroup : vendor.dailyGroups()) {
                int facturaIndex = 0;

                // Por cada factura del día
                for (VendorInvoiceRowDTO invoice : dailyGroup.facturas()) {
                    Row row = sheet.createRow(rowNum++);

                    // Fecha
                    row.createCell(0).setCellValue(invoice.fecha().format(DATE_FORMATTER));

                    // # Factura
                    row.createCell(1).setCellValue(invoice.numeroFactura());

                    // # Cliente
                    row.createCell(2).setCellValue(invoice.numeroCliente());

                    // Valor individual
                    Cell valorCell = row.createCell(3);
                    valorCell.setCellValue(invoice.valor().doubleValue());
                    valorCell.setCellStyle(currencyStyle);

                    // Total del día (solo en la ÚLTIMA factura del día)
                    Cell totalDiaCell = row.createCell(4);
                    if (facturaIndex == dailyGroup.facturas().size() - 1) {
                        // Última factura del día: mostrar total
                        totalDiaCell.setCellValue(dailyGroup.totalDia().doubleValue());
                        totalDiaCell.setCellStyle(currencyStyle);
                    } else {
                        // Facturas anteriores: dejar vacío
                        totalDiaCell.setCellValue("");
                    }

                    facturaIndex++;
                }
            }

            // Fila final: Total período
            Row totalRow = sheet.createRow(rowNum + 1);
            Cell totalLabelCell = totalRow.createCell(3);
            totalLabelCell.setCellValue("TOTAL VENDEDORA:");
            totalLabelCell.setCellStyle(headerStyle);

            Cell totalValueCell = totalRow.createCell(4);
            totalValueCell.setCellValue(vendor.totalPeriod().doubleValue());
            totalValueCell.setCellStyle(currencyStyle);

            // Ajustar ancho de columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
        }
    }

}
