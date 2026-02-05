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
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ClientBalanceService;
import org.example.sistema_gestion_vitalexa.service.ReportExportService;
import org.example.sistema_gestion_vitalexa.service.ReportService;
import org.example.sistema_gestion_vitalexa.util.UserUnificationUtil;
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
    private final ClientBalanceService clientBalanceService;
    private final UserRepository userRepository;
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
        return exportReportToExcel(report, startDate, endDate, null);
    }

    @Override
    public byte[] exportReportToExcel(ReportDTO report, LocalDate startDate, LocalDate endDate,
            java.util.UUID vendorId) {
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

            // HOJA 4: VENDEDORES (Si es filtrado, mostrará solo el vendedor o todos según
            // lo que venga en el DTO)
            createVendorsSheet(workbook, report.vendorReport(), headerStyle, dataStyle, currencyStyle);

            // HOJA 5: CLIENTES TOP
            createTopClientsSheet(workbook, report.clientReport(), headerStyle, dataStyle, currencyStyle);

            // HOJA 6: VENTAS DIARIAS DESGLOSADAS
            List<VendorDailySalesDTO> vendorSalesReports;
            if (vendorId != null) {
                // Get vendor to check if it's a shared user
                User vendor = userRepository.findById(vendorId)
                        .orElseThrow(() -> new org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption(
                                "Vendedor no encontrado"));

                // For shared users, get both usernames
                List<String> matchUsernames;
                if (UserUnificationUtil.isSharedUser(vendor.getUsername())) {
                    matchUsernames = UserUnificationUtil.getSharedUsernames(vendor.getUsername());
                } else {
                    matchUsernames = List.of(vendor.getUsername());
                }

                // Filter by any of the shared usernames
                List<VendorDailySalesDTO> matchingReports = reportService.getVendorDailySalesReport(startDate, endDate)
                        .stream()
                        .filter(v -> matchUsernames.contains(v.vendedorName()))
                        .toList();

                if (matchingReports.isEmpty()) {
                    throw new org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption(
                            "No se encontraron ventas para este vendedor");
                }

                // If there are multiple reports (shared users), merge them
                if (matchingReports.size() > 1) {
                    // Merge all daily groups from both users
                    List<VendorDailyGroupDTO> mergedDailyGroups = new java.util.ArrayList<>();
                    BigDecimal totalPeriod = BigDecimal.ZERO;
                    for (VendorDailySalesDTO vendorReport : matchingReports) {
                        mergedDailyGroups.addAll(vendorReport.dailyGroups());
                        totalPeriod = totalPeriod.add(vendorReport.totalPeriod());
                    }

                    // Create unified report with canonical name (first shared username - Nina)
                    // Use the first report's vendorId, startDate, endDate
                    VendorDailySalesDTO firstReport = matchingReports.get(0);
                    VendorDailySalesDTO unifiedReport = new VendorDailySalesDTO(
                            firstReport.vendedorId(),
                            matchUsernames.get(0), // Use canonical name (NinaTorres)
                            firstReport.startDate(),
                            firstReport.endDate(),
                            mergedDailyGroups,
                            totalPeriod);
                    vendorSalesReports = List.of(unifiedReport);
                } else {
                    vendorSalesReports = matchingReports;
                }
            } else {
                vendorSalesReports = reportService.getVendorDailySalesReport(startDate, endDate);
            }
            createVendorDailySalesSheets(workbook, vendorSalesReports, headerStyle, dataStyle, currencyStyle);

            // HOJA 7: SALDO POR CLIENTE (GENERAL Y POR VENDEDOR)
            List<ClientBalanceDTO> clientBalances;
            if (vendorId != null) {
                clientBalances = clientBalanceService.getClientBalancesByVendedor(vendorId);
                // Si es reporte de un vendedor específico, solo una hoja
                createClientBalanceSheetInternal(workbook, "Saldo " + getVendorNameSafe(workbook, clientBalances),
                        clientBalances, headerStyle, dataStyle, currencyStyle);
            } else {
                clientBalances = clientBalanceService.getAllClientBalances();
                // 1. Hoja General
                createClientBalanceSheetInternal(workbook, "Saldo General", clientBalances, headerStyle, dataStyle,
                        currencyStyle);

                // 2. Hojas por Vendedor
                // Agrupar por vendedor
                java.util.Map<String, java.util.List<ClientBalanceDTO>> balancesByVendor = clientBalances.stream()
                        .collect(java.util.stream.Collectors.groupingBy(b -> {
                            String vName = b.vendedorAsignadoName() != null ? b.vendedorAsignadoName() : "Sin Asignar";
                            if (UserUnificationUtil.isSharedUser(vName)) {
                                return UserUnificationUtil.getSharedUsernames(vName).get(0);
                            }
                            return vName;
                        }));

                for (java.util.Map.Entry<String, java.util.List<ClientBalanceDTO>> entry : balancesByVendor
                        .entrySet()) {
                    String vendorName = entry.getKey();
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        String sheetName = "Saldo " + vendorName;
                        createClientBalanceSheetInternal(workbook, sheetName, entry.getValue(), headerStyle, dataStyle,
                                currencyStyle);
                    }
                }
            }

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
            csvWriter.writeNext(new String[] { "REPORTE GENERAL DE GESTIÓN - VITALEXA" });
            csvWriter.writeNext(new String[] {
                    "Período: " + startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER) });
            csvWriter.writeNext(new String[] { "" }); // línea en blanco
            // VENTAS
            csvWriter.writeNext(new String[] { "===== RESUMEN DE VENTAS =====" });
            csvWriter.writeNext(new String[] { "Métrica", "Valor" });
            csvWriter.writeNext(new String[] { "Ingresos Totales", "$" + report.salesReport().totalRevenue() });
            csvWriter.writeNext(new String[] { "Total Órdenes", String.valueOf(report.salesReport().totalOrders()) });
            csvWriter.writeNext(
                    new String[] { "Órdenes Completadas", String.valueOf(report.salesReport().completedOrders()) });
            csvWriter.writeNext(new String[] { "Valor Promedio", "$" + report.salesReport().averageOrderValue() });
            csvWriter.writeNext(new String[] { "" });

            // PRODUCTOS
            csvWriter.writeNext(new String[] { "===== INVENTARIO DE PRODUCTOS =====" });
            csvWriter.writeNext(
                    new String[] { "Total Productos", String.valueOf(report.productReport().totalProducts()) });
            csvWriter.writeNext(
                    new String[] { "Productos Activos", String.valueOf(report.productReport().activeProducts()) });
            csvWriter
                    .writeNext(new String[] { "Valor Inventario", "$" + report.productReport().totalInventoryValue() });
            csvWriter.writeNext(new String[] { "" });

            // TOP PRODUCTOS
            csvWriter.writeNext(new String[] { "Producto", "Cantidad Vendida", "Ingresos" });
            report.productReport().topSellingProducts().forEach(p -> csvWriter
                    .writeNext(new String[] { p.productName(), String.valueOf(p.quantitySold()), "$" + p.revenue() }));
            csvWriter.writeNext(new String[] { "" });

            // VENDEDORES
            csvWriter.writeNext(new String[] { "===== TOP VENDEDORES =====" });
            csvWriter.writeNext(new String[] { "Vendedor", "Órdenes", "Ingresos", "Promedio por Orden" });
            report.vendorReport().topVendors().forEach(v -> csvWriter.writeNext(new String[] {
                    v.vendorName(),
                    String.valueOf(v.totalOrders()),
                    "$" + v.totalRevenue(),
                    "$" + v.averageOrderValue()
            }));
            csvWriter.writeNext(new String[] { "" });

            // CLIENTES
            csvWriter.writeNext(new String[] { "===== TOP CLIENTES =====" });
            csvWriter.writeNext(new String[] { "Cliente", "Teléfono", "Total Compras", "Órdenes" });
            report.clientReport().topClients().forEach(c -> csvWriter.writeNext(new String[] {
                    c.clientName(),
                    c.clientPhone() != null ? c.clientPhone() : "N/A",
                    "$" + c.totalSpent(),
                    String.valueOf(c.totalOrders())
            }));

            String csvContent = sw.toString();
            // ✅ Agregar BOM UTF-8 para que Excel lo reconozca
            byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
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

            csvWriter.writeNext(new String[] { "REPORTE DE CLIENTES - VITALEXA" });
            csvWriter.writeNext(new String[] { "" });
            csvWriter.writeNext(new String[] { "Total Clientes", String.valueOf(clientReport.totalClients()) });
            csvWriter.writeNext(new String[] { "Clientes Activos", String.valueOf(clientReport.activeClients()) });
            csvWriter.writeNext(new String[] { "" });
            csvWriter.writeNext(new String[] { "Cliente", "Teléfono", "Total Compras", "Número de Órdenes" });

            clientReport.topClients().forEach(c -> csvWriter.writeNext(new String[] {
                    c.clientName(),
                    c.clientPhone() != null ? c.clientPhone() : "N/A",
                    "$" + c.totalSpent(),
                    String.valueOf(c.totalOrders())
            }));

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
                "Período: " + startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER)).setFontSize(12)
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

        Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
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

        Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
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

            Table topTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 2 }))
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

        Table table = new Table(UnitValue.createPercentArray(new float[] { 2, 1, 2, 2 }))
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

        Table summary = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
                .useAllAvailableWidth();

        addPdfTableCell(summary, "Total Clientes:", true);
        addPdfTableCell(summary, String.valueOf(clients.totalClients()), false);

        addPdfTableCell(summary, "Clientes Activos:", true);
        addPdfTableCell(summary, String.valueOf(clients.activeClients()), false);

        document.add(summary);

        if (!clients.topClients().isEmpty()) {
            Table topTable = new Table(UnitValue.createPercentArray(new float[] { 3, 2, 2, 1 }))
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

        addExcelSummaryRow(sheet, rowNum++, "Ingresos Totales", report.salesReport().totalRevenue(), dataStyle,
                currencyStyle);
        addExcelSummaryRow(sheet, rowNum++, "Total Órdenes", report.salesReport().totalOrders(), dataStyle);
        addExcelSummaryRow(sheet, rowNum++, "Órdenes Completadas", report.salesReport().completedOrders(), dataStyle);
        addExcelSummaryRow(sheet, rowNum++, "Valor Promedio", report.salesReport().averageOrderValue(), dataStyle,
                currencyStyle);
        rowNum++;

        Row productHeader = sheet.createRow(rowNum++);
        productHeader.createCell(0).setCellValue("PRODUCTOS");
        productHeader.getCell(0).setCellStyle(headerStyle);

        addExcelSummaryRow(sheet, rowNum++, "Total Productos", report.productReport().totalProducts(), dataStyle);
        addExcelSummaryRow(sheet, rowNum++, "Productos Activos", report.productReport().activeProducts(), dataStyle);
        addExcelSummaryRow(sheet, rowNum++, "Valor Inventario", report.productReport().totalInventoryValue(), dataStyle,
                currencyStyle);

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
        String[] headers = { "Fecha", "Ingresos", "Órdenes" };
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
        String[] headers = { "Producto", "Cantidad Vendida", "Ingresos" };
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
        String[] headers = { "Producto", "Stock Actual", "Estado" };
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
        String[] headers = { "Vendedor", "Órdenes", "Ingresos", "Promedio" };
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
        String[] headers = { "Cliente", "Teléfono", "Total Compras", "Órdenes" };
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
     * Agrupado por: Día -> Cliente -> Facturas
     * Columnas: Fecha | # Factura | # Cliente | Valor | Subtotal Cliente | Total
     * Día
     * Colores: Verde = PAID, Gris = PARTIAL, Sin color = PENDING
     */
    public void createVendorDailySalesSheets(Workbook workbook,
            List<VendorDailySalesDTO> vendorSalesReports,
            CellStyle headerStyle, CellStyle dataStyle, CellStyle currencyStyle) {

        // Crear estilos para estados de pago
        CellStyle paidStyle = workbook.createCellStyle();
        paidStyle.cloneStyleFrom(currencyStyle);
        paidStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_GREEN.getIndex());
        paidStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        CellStyle partialStyle = workbook.createCellStyle();
        partialStyle.cloneStyleFrom(currencyStyle);
        partialStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.YELLOW.getIndex());
        partialStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        CellStyle paidDataStyle = workbook.createCellStyle();
        paidDataStyle.cloneStyleFrom(dataStyle);
        paidDataStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_GREEN.getIndex());
        paidDataStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        CellStyle partialDataStyle = workbook.createCellStyle();
        partialDataStyle.cloneStyleFrom(dataStyle);
        partialDataStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.YELLOW.getIndex());
        partialDataStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

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
                    titleRow.getRowNum(), titleRow.getRowNum(), 0, 5));

            // Período
            Row periodRow = sheet.createRow(rowNum++);
            periodRow.createCell(0).setCellValue("Período:");
            periodRow.createCell(1).setCellValue(
                    vendor.startDate().format(DATE_FORMATTER) + " a " +
                            vendor.endDate().format(DATE_FORMATTER));
            rowNum++;

            // Encabezados - con columnas de descuento y saldo pendiente
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = { "Fecha", "# Factura", "# Cliente", "Valor Original", "Dto%", "Valor Final",
                    "Pagado", "Pendiente", "Subtotal Cliente", "VALOR A COBRAR", "Total Día" }; // Added VALOR A COBRAR
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            BigDecimal totalPendingPeriod = BigDecimal.ZERO;
            BigDecimal totalPaidPeriod = BigDecimal.ZERO;

            // Mapa para facturas pendientes por cliente: Cliente -> List de [InvoiceNum,
            // Fecha, Amount]
            java.util.Map<String, java.util.List<Object[]>> clientPendingInvoices = new java.util.LinkedHashMap<>();

            // Datos: iterar por cada día
            for (VendorDailyGroupDTO dailyGroup : vendor.dailyGroups()) {
                int clientIndex = 0;

                // Por cada cliente del día
                for (ClientDailyGroupDTO clientGroup : dailyGroup.clientGroups()) {
                    int facturaIndex = 0;
                    BigDecimal clientTotalPending = BigDecimal.ZERO;

                    // Calcular pendiente del cliente para este día
                    for (VendorInvoiceRowDTO inv : clientGroup.facturas()) {
                        clientTotalPending = clientTotalPending.add(inv.pendingAmount());
                        totalPendingPeriod = totalPendingPeriod.add(inv.pendingAmount());
                        totalPaidPeriod = totalPaidPeriod.add(inv.paidAmount());

                        // Agregar factura si tiene saldo pendiente
                        if (inv.pendingAmount().compareTo(BigDecimal.ZERO) > 0) {
                            String clientKey = inv.numeroCliente();
                            clientPendingInvoices.computeIfAbsent(clientKey, k -> new java.util.ArrayList<>())
                                    .add(new Object[] { inv.numeroFactura(), inv.fecha(), inv.pendingAmount() });
                        }
                    }

                    // Por cada factura del cliente
                    for (VendorInvoiceRowDTO invoice : clientGroup.facturas()) {
                        Row row = sheet.createRow(rowNum++);

                        // Determinar estilo según estado de pago (Logica explícita)
                        CellStyle rowDataStyle = dataStyle;
                        CellStyle rowCurrencyStyle = currencyStyle;

                        boolean isPaid = invoice.pendingAmount().compareTo(BigDecimal.ZERO) <= 0;
                        boolean isPartial = !isPaid && invoice.paidAmount().compareTo(BigDecimal.ZERO) > 0;

                        if (isPaid) {
                            rowDataStyle = paidDataStyle;
                            rowCurrencyStyle = paidStyle;
                        } else if (isPartial) {
                            rowDataStyle = partialDataStyle;
                            rowCurrencyStyle = partialStyle;
                        }

                        int colNum = 0;

                        // Fecha
                        Cell fechaCell = row.createCell(colNum++);
                        fechaCell.setCellValue(invoice.fecha().format(DATE_FORMATTER));
                        fechaCell.setCellStyle(rowDataStyle);

                        // # Factura
                        Cell facturaCell = row.createCell(colNum++);
                        facturaCell.setCellValue(invoice.numeroFactura());
                        facturaCell.setCellStyle(rowDataStyle);

                        // # Cliente
                        Cell clienteCell = row.createCell(colNum++);
                        clienteCell.setCellValue(invoice.numeroCliente());
                        clienteCell.setCellStyle(rowDataStyle);

                        // Valor Original
                        Cell valorOrigCell = row.createCell(colNum++);
                        valorOrigCell.setCellValue(invoice.valorOriginal().doubleValue());
                        valorOrigCell.setCellStyle(rowCurrencyStyle);

                        // Descuento %
                        Cell discountCell = row.createCell(colNum++);
                        if (invoice.discountPercent() != null
                                && invoice.discountPercent().compareTo(java.math.BigDecimal.ZERO) > 0) {
                            discountCell.setCellValue(invoice.discountPercent().doubleValue() + "%");
                        } else {
                            discountCell.setCellValue("-");
                        }
                        discountCell.setCellStyle(rowDataStyle);

                        // Valor Final
                        Cell valorFinalCell = row.createCell(colNum++);
                        valorFinalCell.setCellValue(invoice.valorFinal().doubleValue());
                        valorFinalCell.setCellStyle(rowCurrencyStyle);

                        // Pagado
                        Cell paidCell = row.createCell(colNum++);
                        paidCell.setCellValue(invoice.paidAmount().doubleValue());
                        paidCell.setCellStyle(rowCurrencyStyle);

                        // Pendiente
                        Cell pendingCell = row.createCell(colNum++);
                        pendingCell.setCellValue(invoice.pendingAmount().doubleValue());
                        pendingCell.setCellStyle(rowCurrencyStyle);

                        // Subtotal cliente (Totla Compra) (solo en la ÚLTIMA factura del cliente)
                        Cell subtotalCell = row.createCell(colNum++);
                        if (facturaIndex == clientGroup.facturas().size() - 1) {
                            subtotalCell.setCellValue(clientGroup.subtotalCliente().doubleValue());
                        }
                        subtotalCell.setCellStyle(rowCurrencyStyle);

                        // VALOR A COBRAR (Total Pendiente Cliente) (solo en la ÚLTIMA factura del
                        // cliente)
                        Cell toCollectCell = row.createCell(colNum++);
                        if (facturaIndex == clientGroup.facturas().size() - 1) {
                            toCollectCell.setCellValue(clientTotalPending.doubleValue());
                        }
                        toCollectCell.setCellStyle(rowCurrencyStyle);

                        // Total del día (solo en la ÚLTIMA factura del ÚLTIMO cliente del día)
                        Cell totalDiaCell = row.createCell(colNum++);
                        if (clientIndex == dailyGroup.clientGroups().size() - 1
                                && facturaIndex == clientGroup.facturas().size() - 1) {
                            totalDiaCell.setCellValue(dailyGroup.totalDia().doubleValue());
                        }
                        totalDiaCell.setCellStyle(rowCurrencyStyle);

                        facturaIndex++;
                    }
                    clientIndex++;
                }
            }

            // Fila final: Total período
            rowNum++;
            Row totalRow = sheet.createRow(rowNum++);
            Cell totalLabelCell = totalRow.createCell(9); // Shifted
            totalLabelCell.setCellValue("TOTAL VENDEDORA:");
            totalLabelCell.setCellStyle(headerStyle);

            Cell totalValueCell = totalRow.createCell(10); // Shifted
            totalValueCell.setCellValue(vendor.totalPeriod().doubleValue());
            totalValueCell.setCellStyle(currencyStyle);

            // Fila Total Cartera
            Row portfolioRow = sheet.createRow(rowNum++);
            Cell portfolioLabel = portfolioRow.createCell(9);
            portfolioLabel.setCellValue("TOTAL CARTERA:");
            portfolioLabel.setCellStyle(headerStyle);

            Cell portfolioValue = portfolioRow.createCell(10);
            portfolioValue.setCellValue(totalPendingPeriod.doubleValue());
            portfolioValue.setCellStyle(currencyStyle);

            // Fila Total Cobrado
            Row collectedRow = sheet.createRow(rowNum++);
            Cell collectedLabel = collectedRow.createCell(9);
            collectedLabel.setCellValue("TOTAL COBRADO:");
            collectedLabel.setCellStyle(headerStyle);

            Cell collectedValue = collectedRow.createCell(10);
            collectedValue.setCellValue(totalPaidPeriod.doubleValue());
            collectedValue.setCellStyle(currencyStyle);

            // ========================================
            // TABLA RESUMEN: CARTERA POR CLIENTE (a la derecha)
            // ========================================
            int summaryStartRow = 3; // Comenzar después del título y período
            int summaryCol = 13; // Columna después de "Total Día"

            // Título del resumen
            Row summaryTitleRow = sheet.getRow(summaryStartRow);
            if (summaryTitleRow == null) {
                summaryTitleRow = sheet.createRow(summaryStartRow);
            }
            Cell summaryTitleCell = summaryTitleRow.createCell(summaryCol);
            summaryTitleCell.setCellValue("RESUMEN CARTERA POR CLIENTE");
            summaryTitleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                    summaryStartRow, summaryStartRow, summaryCol, summaryCol + 3));

            // Encabezados del resumen (ahora 4 columnas)
            int summaryHeaderRow = summaryStartRow + 1;
            Row clientSummaryHeaderRow = sheet.getRow(summaryHeaderRow);
            if (clientSummaryHeaderRow == null) {
                clientSummaryHeaderRow = sheet.createRow(summaryHeaderRow);
            }

            Cell clientHeaderCell = clientSummaryHeaderRow.createCell(summaryCol);
            clientHeaderCell.setCellValue("Cliente");
            clientHeaderCell.setCellStyle(headerStyle);

            Cell invoiceHeaderCell = clientSummaryHeaderRow.createCell(summaryCol + 1);
            invoiceHeaderCell.setCellValue("# Factura");
            invoiceHeaderCell.setCellStyle(headerStyle);

            Cell dateHeaderCell = clientSummaryHeaderRow.createCell(summaryCol + 2);
            dateHeaderCell.setCellValue("Fecha");
            dateHeaderCell.setCellStyle(headerStyle);

            Cell debtHeaderCell = clientSummaryHeaderRow.createCell(summaryCol + 3);
            debtHeaderCell.setCellValue("Debe");
            debtHeaderCell.setCellStyle(headerStyle);

            // Datos del resumen: Listar facturas con deuda > 0, agrupadas por cliente
            int summaryDataRow = summaryHeaderRow + 1;

            for (java.util.Map.Entry<String, java.util.List<Object[]>> entry : clientPendingInvoices.entrySet()) {
                String clientName = entry.getKey();
                java.util.List<Object[]> clientInvoices = entry.getValue();

                // Ordenar por fecha (índice 1 del array)
                clientInvoices.sort((a, b) -> ((LocalDate) a[1]).compareTo((LocalDate) b[1]));

                for (int i = 0; i < clientInvoices.size(); i++) {
                    Object[] inv = clientInvoices.get(i);
                    Row clientRow = sheet.getRow(summaryDataRow);
                    if (clientRow == null) {
                        clientRow = sheet.createRow(summaryDataRow);
                    }

                    // Mostrar nombre del cliente solo en la primera fila
                    Cell clientNameCell = clientRow.createCell(summaryCol);
                    if (i == 0) {
                        clientNameCell.setCellValue(clientName);
                    }
                    clientNameCell.setCellStyle(dataStyle);

                    // # Factura
                    Cell invoiceNumCell = clientRow.createCell(summaryCol + 1);
                    invoiceNumCell.setCellValue((String) inv[0]);
                    invoiceNumCell.setCellStyle(dataStyle);

                    // Fecha
                    Cell dateCell = clientRow.createCell(summaryCol + 2);
                    dateCell.setCellValue(((LocalDate) inv[1]).format(DATE_FORMATTER));
                    dateCell.setCellStyle(dataStyle);

                    // Debe
                    Cell debtCell = clientRow.createCell(summaryCol + 3);
                    debtCell.setCellValue(((BigDecimal) inv[2]).doubleValue());
                    debtCell.setCellStyle(currencyStyle);

                    summaryDataRow++;
                }

                // Línea de subtotal por cliente si tiene más de una factura
                if (clientInvoices.size() > 1) {
                    Row subtotalRow = sheet.getRow(summaryDataRow);
                    if (subtotalRow == null) {
                        subtotalRow = sheet.createRow(summaryDataRow);
                    }

                    Cell subtotalLabelCell = subtotalRow.createCell(summaryCol + 2);
                    subtotalLabelCell.setCellValue("Subtotal:");
                    subtotalLabelCell.setCellStyle(headerStyle);

                    BigDecimal clientTotal = clientInvoices.stream()
                            .map(a -> (BigDecimal) a[2])
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Cell subtotalValueCell = subtotalRow.createCell(summaryCol + 3);
                    subtotalValueCell.setCellValue(clientTotal.doubleValue());
                    subtotalValueCell.setCellStyle(currencyStyle);

                    summaryDataRow++;
                }
            }

            // Total del resumen
            Row summaryTotalRow = sheet.getRow(summaryDataRow);
            if (summaryTotalRow == null) {
                summaryTotalRow = sheet.createRow(summaryDataRow);
            }
            Cell summaryTotalLabel = summaryTotalRow.createCell(summaryCol + 2);
            summaryTotalLabel.setCellValue("TOTAL:");
            summaryTotalLabel.setCellStyle(headerStyle);

            Cell summaryTotalValue = summaryTotalRow.createCell(summaryCol + 3);
            summaryTotalValue.setCellValue(totalPendingPeriod.doubleValue());
            summaryTotalValue.setCellStyle(currencyStyle);

            // Ajustar ancho de columnas del resumen
            sheet.autoSizeColumn(summaryCol);
            sheet.autoSizeColumn(summaryCol + 1);
            sheet.autoSizeColumn(summaryCol + 2);
            sheet.autoSizeColumn(summaryCol + 3);

            // Ajustar ancho de columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
        }
    }

    // =============================================
    // HOJA DE SALDO POR CLIENTE
    // =============================================
    private String getVendorNameSafe(Workbook workbook, List<ClientBalanceDTO> balances) {
        if (balances.isEmpty())
            return "Vendedor";
        String vName = balances.get(0).vendedorAsignadoName();
        if (vName == null) {
            return "Sin Asignar";
        }
        // If this is a shared user, use the canonical name (NinaTorres)
        if (UserUnificationUtil.isSharedUser(vName)) {
            return UserUnificationUtil.getSharedUsernames(vName).get(0);
        }
        return vName;
    }

    private void createClientBalanceSheetInternal(Workbook workbook, String sheetName, List<ClientBalanceDTO> balances,
            CellStyle headerStyle,
            CellStyle dataStyle, CellStyle currencyStyle) {
        // Sanitizar nombre de hoja
        String safeName = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(sheetName);
        Sheet sheet = workbook.createSheet(safeName);
        int rowNum = 0;

        // Título
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(sheetName.toUpperCase());
        titleCell.setCellStyle(headerStyle);
        rowNum++;

        // Encabezados
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                "Cliente", "Vendedora", "Tope Crédito", "Saldo Inicial",
                "Total Órdenes", "Total Pagado", "Saldo Pendiente", "# Órdenes Pend."
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Estilos para saldos
        CellStyle paidStyle = workbook.createCellStyle();
        paidStyle.cloneStyleFrom(currencyStyle);
        paidStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        paidStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle partialStyle = workbook.createCellStyle();
        partialStyle.cloneStyleFrom(currencyStyle);
        partialStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        partialStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Estilos para celdas de datos (no moneda)
        CellStyle paidDataStyle = workbook.createCellStyle();
        paidDataStyle.cloneStyleFrom(dataStyle);
        paidDataStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        paidDataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle partialDataStyle = workbook.createCellStyle();
        partialDataStyle.cloneStyleFrom(dataStyle);
        partialDataStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        partialDataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        BigDecimal totalPending = BigDecimal.ZERO;

        for (ClientBalanceDTO balance : balances) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;

            // Determinar si aplica color condicional (Pagado > 0 y Pendiente > 0) ->
            // Partial (Yellow)
            // Si Pendiente <= 0 -> Green (Pagado)
            boolean isPartial = balance.totalPaid().compareTo(BigDecimal.ZERO) > 0
                    && balance.pendingBalance().compareTo(BigDecimal.ZERO) > 0;
            boolean isPaid = balance.pendingBalance().compareTo(BigDecimal.ZERO) <= 0;

            // Estilos de fila según estado de pago - TODA LA FILA se colorea
            CellStyle rowDataStyle = dataStyle;
            CellStyle rowCurrencyStyle = currencyStyle;

            if (isPaid) {
                rowDataStyle = paidDataStyle; // Verde para datos
                rowCurrencyStyle = paidStyle; // Verde para moneda
            } else if (isPartial) {
                rowDataStyle = partialDataStyle; // Amarillo para datos
                rowCurrencyStyle = partialStyle; // Amarillo para moneda
            }

            // Cliente
            Cell clientCell = row.createCell(colNum++);
            clientCell.setCellValue(balance.clientName());
            clientCell.setCellStyle(rowDataStyle);

            // Vendedora
            Cell vendorCell = row.createCell(colNum++);
            String displayVendorName = balance.vendedorAsignadoName() != null ? balance.vendedorAsignadoName() : "-";
            // If this is a shared user, use the canonical name (NinaTorres)
            if (!"-".equals(displayVendorName) && UserUnificationUtil.isSharedUser(displayVendorName)) {
                displayVendorName = UserUnificationUtil.getSharedUsernames(displayVendorName).get(0);
            }
            vendorCell.setCellValue(displayVendorName);
            vendorCell.setCellStyle(rowDataStyle);

            // Tope Crédito
            Cell creditLimitCell = row.createCell(colNum++);
            if (balance.creditLimit() != null && balance.creditLimit().compareTo(BigDecimal.ZERO) > 0) {
                creditLimitCell.setCellValue(balance.creditLimit().doubleValue());
                creditLimitCell.setCellStyle(rowCurrencyStyle); // AHORA usa estilo de fila
            } else {
                creditLimitCell.setCellValue("Sin límite");
                creditLimitCell.setCellStyle(rowDataStyle); // AHORA usa estilo de fila
            }

            // Saldo Inicial
            Cell initialCell = row.createCell(colNum++);
            initialCell.setCellValue(balance.initialBalance() != null ? balance.initialBalance().doubleValue() : 0);
            initialCell.setCellStyle(rowCurrencyStyle); // AHORA usa estilo de fila

            // Total Órdenes
            Cell totalOrdersCell = row.createCell(colNum++);
            totalOrdersCell.setCellValue(balance.totalOrders().doubleValue());
            totalOrdersCell.setCellStyle(rowCurrencyStyle); // AHORA usa estilo de fila

            // Total Pagado
            Cell paidCell = row.createCell(colNum++);
            paidCell.setCellValue(balance.totalPaid().doubleValue());
            paidCell.setCellStyle(rowCurrencyStyle); // AHORA usa estilo de fila

            // Saldo Pendiente
            Cell pendingCell = row.createCell(colNum++);
            pendingCell.setCellValue(balance.pendingBalance().doubleValue());
            pendingCell.setCellStyle(rowCurrencyStyle);

            totalPending = totalPending.add(balance.pendingBalance());

            // # Órdenes Pendientes
            Cell ordersCountCell = row.createCell(colNum++);
            ordersCountCell.setCellValue(balance.pendingOrdersCount());
            ordersCountCell.setCellStyle(rowDataStyle);
        }

        // Fila de totales
        rowNum++;
        Row totalRow = sheet.createRow(rowNum);
        Cell totalLabelCell = totalRow.createCell(5);
        totalLabelCell.setCellValue("TOTAL PENDIENTE:");
        totalLabelCell.setCellStyle(headerStyle);

        Cell totalValueCell = totalRow.createCell(6);
        totalValueCell.setCellValue(totalPending.doubleValue());
        totalValueCell.setCellStyle(currencyStyle);

        // Ajustar ancho de columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Exportar Excel para un vendedor específico (solo su hoja)
     */
    @Override
    public byte[] exportVendorReportExcel(VendorDailySalesDTO vendorReport) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);

            // Crear solo UNA hoja para este vendedor
            java.util.List<VendorDailySalesDTO> singleVendorList = java.util.List.of(vendorReport);
            createVendorDailySalesSheets(workbook, singleVendorList, headerStyle, dataStyle, currencyStyle);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando Excel para vendedor: {}", vendorReport.vendedorName(), e);
            throw new RuntimeException("Error al generar reporte de vendedor Excel", e);
        }
    }

    /**
     * Exportar PDF para un vendedor específico
     */
    @Override
    public byte[] exportVendorReportPdf(VendorDailySalesDTO vendorReport) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(baos);
            com.itextpdf.kernel.pdf.PdfDocument pdf = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);

            // Título
            com.itextpdf.layout.element.Paragraph title = new com.itextpdf.layout.element.Paragraph(
                    "REPORTE DE VENTAS - " + vendorReport.vendedorName().toUpperCase())
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
            document.add(title);

            // Período
            com.itextpdf.layout.element.Paragraph period = new com.itextpdf.layout.element.Paragraph(
                    "Período: " + vendorReport.startDate().format(DATE_FORMATTER) + " a " +
                            vendorReport.endDate().format(DATE_FORMATTER))
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(period);

            // Tabla de datos
            com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(
                    com.itextpdf.layout.properties.UnitValue.createPercentArray(
                            new float[] { 1.5f, 1.5f, 1.5f, 2, 1.5f, 2, 1.5f, 1.5f, 2, 2 }))
                    .useAllAvailableWidth();

            // Encabezados
            String[] headers = { "Fecha", "# Factura", "Cliente", "Valor Original", "Dto%",
                    "Valor Final", "Pagado", "Pendiente", "Subtotal Cliente", "Total Día" };

            for (String header : headers) {
                com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(header).setBold())
                        .setBackgroundColor(new com.itextpdf.kernel.colors.DeviceRgb(52, 73, 94))
                        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                        .setPadding(8);
                table.addCell(cell);
            }

            // Datos - iterar por cada día
            for (org.example.sistema_gestion_vitalexa.dto.VendorDailyGroupDTO dailyGroup : vendorReport.dailyGroups()) {
                // Para simplificar, mostrar solo el resumen del día
                // (la implementación completa sería similar a Excel)
                com.itextpdf.layout.element.Cell dateCell = new com.itextpdf.layout.element.Cell(1, headers.length)
                        .add(new com.itextpdf.layout.element.Paragraph(
                                "Fecha: " + dailyGroup.fecha().format(DATE_FORMATTER) +
                                        " | Total Día: $" + dailyGroup.totalDia()))
                        .setBackgroundColor(new com.itextpdf.kernel.colors.DeviceRgb(240, 240, 240))
                        .setPadding(6);
                table.addCell(dateCell);
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF para vendedor: {}", vendorReport.vendedorName(), e);
            throw new RuntimeException("Error al generar reporte de vendedor PDF", e);
        }
    }
}
