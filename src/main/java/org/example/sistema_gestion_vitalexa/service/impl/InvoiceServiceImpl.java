package org.example.sistema_gestion_vitalexa.service.impl;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;  // ✅ CAMBIO AQUÍ
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.OrderItem;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.service.InvoiceService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final OrdenRepository ordenRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DeviceRgb BRAND_COLOR = new DeviceRgb(52, 73, 94);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(240, 240, 240);

    @Override
    public byte[] generateOrderInvoicePdf(UUID orderId) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Obtener orden completa
            Order order = ordenRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // ===== HEADER DE LA EMPRESA =====
            addCompanyHeader(document);

            // ===== INFORMACIÓN DE LA ORDEN =====
            addOrderInfo(document, order);

            // ===== INFORMACIÓN DEL CLIENTE (si existe) =====
            if (order.getCliente() != null) {
                addClientInfo(document, order);
            }

            // ===== TABLA DE PRODUCTOS =====
            addProductsTable(document, order);

            // ===== TOTALES =====
            addTotals(document, order);

            // ===== NOTAS (si existen) =====
            if (order.getNotas() != null && !order.getNotas().isBlank()) {
                addNotes(document, order);
            }

            // ===== FOOTER =====
            addFooter(document);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando factura PDF para orden {}", orderId, e);
            throw new RuntimeException("Error al generar factura PDF", e);
        }
    }

    @Override
    public void sendInvoiceByEmail(UUID orderId) {
        // TODO: Implementar envío por email en la siguiente fase
        log.info("Funcionalidad de envío por email pendiente de implementación");
        throw new UnsupportedOperationException("Envío por email próximamente disponible");
    }

    // =============================================
    // MÉTODOS AUXILIARES PARA PDF
    // =============================================

    private void addCompanyHeader(Document document) {
        // Logo o nombre de la empresa
        try {
            ImageData imageData = ImageDataFactory.create("src/main/resources/static/images/logo.png");
            Image logo = new Image(imageData);
            logo.setWidth(200);
            logo.setHeight(100);
            logo.setHorizontalAlignment(HorizontalAlignment.CENTER);
            document.add(logo);
        } catch (Exception e) {
            // Fallback si no encuentra el logo
            Paragraph companyName = new Paragraph("VITALEXA");
        }

        Paragraph slogan = new Paragraph("Sistema de Gestión de Pedidos")
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(slogan);


        SolidLine lineDrawer = new SolidLine();
        lineDrawer.setColor(BRAND_COLOR);
        lineDrawer.setLineWidth(2f);
        LineSeparator separator = new LineSeparator(lineDrawer);
        document.add(separator);
        document.add(new Paragraph("\n"));
    }

    private void addOrderInfo(Document document, Order order) {
        // Título
        Paragraph title = new Paragraph("ORDEN DE PEDIDO")
                .setFontSize(18)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(15);
        document.add(title);

        // Información en tabla
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(15);

        addInfoRow(infoTable, "N° Orden:", order.getInvoiceNumber().toString().toUpperCase());
        addInfoRow(infoTable, "Fecha:", order.getFecha().format(DATE_FORMATTER));
        addInfoRow(infoTable, "Estado:", order.getEstado().toString());
        addInfoRow(infoTable, "Vendedor:", order.getVendedor().getUsername());

        document.add(infoTable);
    }

    private void addClientInfo(Document document, Order order) {
        Paragraph clientTitle = new Paragraph("INFORMACIÓN DEL CLIENTE")
                .setFontSize(14)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(clientTitle);

        Table clientTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth()
                .setMarginBottom(15);

        addInfoRow(clientTable, "Cliente:", order.getCliente().getNombre());

        if (order.getCliente().getTelefono() != null) {
            addInfoRow(clientTable, "Teléfono:", order.getCliente().getTelefono());
        }

        if (order.getCliente().getEmail() != null) {
            addInfoRow(clientTable, "Email:", order.getCliente().getEmail());
        }

        if (order.getCliente().getDireccion() != null) {
            addInfoRow(clientTable, "Dirección:", order.getCliente().getDireccion());
        }

        document.add(clientTable);
    }

    private void addProductsTable(Document document, Order order) {
        Paragraph productsTitle = new Paragraph("DETALLE DE PRODUCTOS")
                .setFontSize(14)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(productsTitle);

        Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 2, 2}))
                .useAllAvailableWidth();

        // Header
        addTableHeaderCell(table, "Producto");
        addTableHeaderCell(table, "Cant.");
        addTableHeaderCell(table, "P. Unitario");
        addTableHeaderCell(table, "Subtotal");

        // Items
        for (OrderItem item : order.getItems()) {
            addTableDataCell(table, item.getProduct().getNombre());
            addTableDataCell(table, String.valueOf(item.getCantidad()));
            addTableDataCell(table, formatCurrency(item.getPrecioUnitario()));
            addTableDataCell(table, formatCurrency(item.getSubTotal()));
        }

        document.add(table);
    }

    private void addTotals(Document document, Order order) {
        Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .useAllAvailableWidth()
                .setMarginTop(15);

        // Subtotal
        com.itextpdf.layout.element.Cell labelCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("SUBTOTAL:").setBold())
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(null)
                .setPadding(5);
        totalsTable.addCell(labelCell);

        com.itextpdf.layout.element.Cell valueCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(formatCurrency(order.getTotal())))
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(null)
                .setPadding(5);
        totalsTable.addCell(valueCell);

        // Total (en este caso es igual, pero puedes agregar impuestos después)
        com.itextpdf.layout.element.Cell totalLabelCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("TOTAL:").setFontSize(14).setBold())
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(null)
                .setBackgroundColor(LIGHT_GRAY)
                .setPadding(8);
        totalsTable.addCell(totalLabelCell);

        com.itextpdf.layout.element.Cell totalValueCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(formatCurrency(order.getTotal())).setFontSize(14).setBold())
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(null)
                .setBackgroundColor(LIGHT_GRAY)
                .setPadding(8);
        totalsTable.addCell(totalValueCell);

        document.add(totalsTable);
    }

    private void addNotes(Document document, Order order) {
        Paragraph notesTitle = new Paragraph("NOTAS")
                .setFontSize(12)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(5);
        document.add(notesTitle);

        Paragraph notesContent = new Paragraph(order.getNotas())
                .setFontSize(10)
                .setItalic()
                .setBackgroundColor(LIGHT_GRAY)
                .setPadding(10);
        document.add(notesContent);
    }

    private void addFooter(Document document) {
        document.add(new Paragraph("\n"));


        SolidLine lineDrawer = new SolidLine();
        lineDrawer.setColor(ColorConstants.LIGHT_GRAY);
        lineDrawer.setLineWidth(1f);
        LineSeparator separator = new LineSeparator(lineDrawer);
        document.add(separator);

        Paragraph footer = new Paragraph("Gracias por su compra - VITALEXA")
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10);
        document.add(footer);

        Paragraph contact = new Paragraph("Contacto: info@vitalexa.com | Tel: +57 300 123 4567")
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY);
        document.add(contact);
    }

    // ===== UTILIDADES =====

    private void addInfoRow(Table table, String label, String value) {
        com.itextpdf.layout.element.Cell labelCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(label).setBold())
                .setBorder(null)
                .setPadding(3);
        table.addCell(labelCell);

        com.itextpdf.layout.element.Cell valueCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(value))
                .setBorder(null)
                .setPadding(3);
        table.addCell(valueCell);
    }

    private void addTableHeaderCell(Table table, String content) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(content).setBold().setFontSize(11))
                .setBackgroundColor(BRAND_COLOR)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(8);
        table.addCell(cell);
    }

    private void addTableDataCell(Table table, String content) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(content).setFontSize(10))
                .setPadding(6)
                .setTextAlignment(TextAlignment.CENTER);
        table.addCell(cell);
    }

    private String formatCurrency(BigDecimal amount) {
        return String.format("$%,.2f", amount);
    }
}
