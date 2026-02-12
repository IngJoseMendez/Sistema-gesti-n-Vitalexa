package org.example.sistema_gestion_vitalexa.service.impl;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.service.ProductAuditService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductAuditServiceImpl implements ProductAuditService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DeviceRgb BRAND_COLOR = new DeviceRgb(52, 73, 94);
    private static final DeviceRgb HEADER_BG_COLOR = new DeviceRgb(240, 240, 240);

    @Override
    public byte[] generateProductAudit(List<ProductResponse> products, String username, String operationType) {
        // Delegate to new method with empty failures
        return generateProductAudit(
                new org.example.sistema_gestion_vitalexa.dto.BulkProductUpdateResult(products, List.of()),
                username,
                operationType);
    }

    @Override
    public byte[] generateProductAudit(org.example.sistema_gestion_vitalexa.dto.BulkProductUpdateResult result,
            String username, String operationType) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // 1. Header
            addHeader(document, username, operationType);

            // 2. Summary
            addSummary(document, result.successful().size(), result.failures().size());

            // 3. Table SUCCESS
            if (!result.successful().isEmpty()) {
                document.add(new Paragraph("PRODUCTOS PROCESADOS CORRECTAMENTE")
                        .setFontSize(12).setBold().setMarginTop(10).setMarginBottom(5));
                addProductsTable(document, result.successful());
            } else if (result.failures().isEmpty()) {
                document.add(new Paragraph("No se procesaron productos.")
                        .setFontSize(10).setItalic().setMarginTop(10));
            }

            // 4. Table FAILURES
            if (!result.failures().isEmpty()) {
                document.add(new Paragraph("ERRORES DE PROCESAMIENTO")
                        .setFontSize(12).setBold().setFontColor(ColorConstants.RED).setMarginTop(15)
                        .setMarginBottom(5));
                addFailuresTable(document, result.failures());
            }

            // 5. Footer
            addFooter(document);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating product audit PDF", e);
            throw new RuntimeException("Error generating audit trail PDF", e);
        }
    }

    private void addFailuresTable(Document document,
            List<org.example.sistema_gestion_vitalexa.dto.BulkProductUpdateResult.BulkError> failures) {
        Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 2, 3 }))
                .useAllAvailableWidth()
                .setFontSize(9);

        // Header
        table.addHeaderCell(
                new Cell().add(new Paragraph("ID Producto").setBold()).setBackgroundColor(ColorConstants.LIGHT_GRAY));
        table.addHeaderCell(
                new Cell().add(new Paragraph("Producto").setBold()).setBackgroundColor(ColorConstants.LIGHT_GRAY));
        table.addHeaderCell(
                new Cell().add(new Paragraph("Error").setBold()).setBackgroundColor(ColorConstants.LIGHT_GRAY));

        for (org.example.sistema_gestion_vitalexa.dto.BulkProductUpdateResult.BulkError error : failures) {
            table.addCell(new Cell().add(new Paragraph(error.id() != null ? error.id().toString() : "N/A")));
            table.addCell(
                    new Cell().add(new Paragraph(error.productName() != null ? error.productName() : "Desconocido")));
            table.addCell(new Cell().add(new Paragraph(error.message()).setFontColor(ColorConstants.RED)));
        }

        document.add(table);
    }

    private void addSummary(Document document, int successCount, int failureCount) {
        Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 1, 1 }))
                .useAllAvailableWidth()
                .setMarginBottom(20);

        table.addCell(new Cell().add(new Paragraph("Total Procesados").setBold())
                .setBackgroundColor(ColorConstants.LIGHT_GRAY));
        table.addCell(
                new Cell().add(new Paragraph("Exitosos").setBold()).setBackgroundColor(new DeviceRgb(200, 255, 200))); // Light
                                                                                                                       // Green
        table.addCell(
                new Cell().add(new Paragraph("Fallidos").setBold()).setBackgroundColor(new DeviceRgb(255, 200, 200))); // Light
                                                                                                                       // Red

        table.addCell(new Cell().add(
                new Paragraph(String.valueOf(successCount + failureCount)).setTextAlignment(TextAlignment.CENTER)));
        table.addCell(
                new Cell().add(new Paragraph(String.valueOf(successCount)).setTextAlignment(TextAlignment.CENTER)));
        table.addCell(
                new Cell().add(new Paragraph(String.valueOf(failureCount)).setTextAlignment(TextAlignment.CENTER)));

        document.add(table);
    }

    private void addHeader(Document document, String username, String operationType) {
        // Logo (optional, try-catch to avoid crash if missing)
        try {
            ImageData imageData = ImageDataFactory.create("src/main/resources/static/images/logo.png");
            Image logo = new Image(imageData);
            logo.setWidth(150);
            logo.setHorizontalAlignment(HorizontalAlignment.LEFT);
            document.add(logo);
        } catch (Exception e) {
            // Logo not found, ignore
        }

        Paragraph title = new Paragraph("HUELLA CONTABLE - " + operationType.toUpperCase())
                .setFontSize(16)
                .setBold()
                .setFontColor(BRAND_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(title);

        Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 1, 3 }))
                .useAllAvailableWidth()
                .setMarginBottom(20);

        addInfoRow(infoTable, "Fecha y Hora:", LocalDateTime.now().format(DATE_FORMATTER));
        addInfoRow(infoTable, "Usuario Responsable:", username != null ? username : "N/A");
        addInfoRow(infoTable, "Tipo de Operación:", operationType);

        document.add(infoTable);
    }

    private void addSummary(Document document, List<ProductResponse> products) {
        int totalQuantity = products.stream().mapToInt(ProductResponse::stock).sum(); // Assuming stock is the added
                                                                                      // quantity for new products
        // Note: For existing products updated, stock might represent total stock.
        // Since this is "Add New Products", we assume the stock value in response is
        // what was added (or the initial stock).

        BigDecimal totalValue = products.stream()
                .map(p -> p.precio().multiply(new BigDecimal(p.stock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Paragraph summaryTitle = new Paragraph("RESUMEN DE LA OPERACIÓN")
                .setFontSize(12)
                .setBold()
                .setFontColor(BRAND_COLOR)
                .setMarginBottom(5);
        document.add(summaryTitle);

        Table summaryTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1, 1 }))
                .useAllAvailableWidth()
                .setMarginBottom(20);

        addHeaderCell(summaryTable, "Total Productos");
        addHeaderCell(summaryTable, "Total Unidades");
        addHeaderCell(summaryTable, "Valor Total Estimado");

        addCell(summaryTable, String.valueOf(products.size()), TextAlignment.CENTER);
        addCell(summaryTable, String.valueOf(totalQuantity), TextAlignment.CENTER);
        addCell(summaryTable, String.format("$%,.2f", totalValue), TextAlignment.CENTER);

        document.add(summaryTable);
    }

    private void addProductsTable(Document document, List<ProductResponse> products) {
        Paragraph tableTitle = new Paragraph("DETALLE DE PRODUCTOS INGRESADOS")
                .setFontSize(12)
                .setBold()
                .setFontColor(BRAND_COLOR)
                .setMarginBottom(5);
        document.add(tableTitle);

        Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1, 1, 1.5f }))
                .useAllAvailableWidth();

        // Headers
        addHeaderCell(table, "Producto / Descripción");
        addHeaderCell(table, "Stock Inicial");
        addHeaderCell(table, "Punto Reorden");
        addHeaderCell(table, "Precio Unit.");
        addHeaderCell(table, "Valor Total");

        for (ProductResponse product : products) {
            addCell(table, product.nombre() != null ? product.nombre() : "N/A", TextAlignment.LEFT);
            addCell(table, String.valueOf(product.stock()), TextAlignment.CENTER);
            addCell(table, String.valueOf(product.reorderPoint()), TextAlignment.CENTER);
            addCell(table, String.format("$%,.2f", product.precio()), TextAlignment.RIGHT);

            BigDecimal lineTotal = product.precio().multiply(new BigDecimal(product.stock()));
            addCell(table, String.format("$%,.2f", lineTotal), TextAlignment.RIGHT);
        }

        document.add(table);
    }

    private void addFooter(Document document) {
        Paragraph footer = new Paragraph(
                "\nEste documento es un comprobante automático de auditoría generado por el sistema VITALEXA.\n" +
                        "Certifica el ingreso de los productos detallados al inventario físico y lógico.")
                .setFontSize(9)
                .setItalic()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginTop(30);
        document.add(footer);
    }

    // --- Helpers ---

    private void addInfoRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold().setFontSize(10)).setBorder(null));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(10)).setBorder(null));
    }

    private void addHeaderCell(Table table, String text) {
        table.addCell(new Cell()
                .add(new Paragraph(text).setBold().setFontSize(10))
                .setBackgroundColor(HEADER_BG_COLOR)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private void addCell(Table table, String text, TextAlignment alignment) {
        table.addCell(new Cell()
                .add(new Paragraph(text).setFontSize(9))
                .setTextAlignment(alignment));
    }
}
