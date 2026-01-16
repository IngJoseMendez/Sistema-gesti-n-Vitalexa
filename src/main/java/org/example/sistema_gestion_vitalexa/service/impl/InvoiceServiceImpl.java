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

            // Detectar si es orden S/R
            boolean isSROrder = order.getNotas() != null && order.getNotas().contains("[S/R]");

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // ===== HEADER DE LA EMPRESA =====
            addCompanyHeader(document, isSROrder);

            // ===== INFORMACIÓN DE LA ORDEN =====
            addOrderInfo(document, order, isSROrder);

            // ===== TABLA DE PRODUCTOS =====
            addProductsTable(document, order, isSROrder);

            // ===== TOTALES =====
            addTotals(document, order, isSROrder);

            // ===== NOTAS (si existen) =====
            if (order.getNotas() != null && !order.getNotas().isBlank()) {
                addNotes(document, order, isSROrder);
            }

            // ===== FOOTER =====
            addFooter(document, isSROrder);

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

    private void addCompanyHeader(Document document, boolean isSROrder) {
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

        // Watermark para facturas S/R
        if (isSROrder) {
            Paragraph watermark = new Paragraph("SIN REGISTRO - S/N")
                    .setFontSize(16)
                    .setBold()
                    .setFontColor(new DeviceRgb(220, 53, 69)) // Rojo
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10);
            document.add(watermark);
        }

        SolidLine lineDrawer = new SolidLine();
        lineDrawer.setColor(isSROrder ? new DeviceRgb(220, 53, 69) : BRAND_COLOR);
        lineDrawer.setLineWidth(isSROrder ? 3f : 2f);
        LineSeparator separator = new LineSeparator(lineDrawer);
        document.add(separator);
        document.add(new Paragraph("\n"));
    }

    private void addOrderInfo(Document document, Order order, boolean isSROrder) {
        // Indicador S/N si es una orden S/R
        if (isSROrder) {
            Paragraph srIndicator = new Paragraph("S/N")
                    .setFontSize(14)
                    .setBold()
                    .setTextAlignment(TextAlignment.LEFT)
                    .setFontColor(new DeviceRgb(220, 53, 69)) // Rojo
                    .setMarginBottom(5);
            document.add(srIndicator);
        }

        // Título con color diferente si es S/N
        Paragraph title = new Paragraph("FACTURA DE PEDIDO")
                .setFontSize(18)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(isSROrder ? new DeviceRgb(220, 53, 69) : BRAND_COLOR)
                .setMarginBottom(15);
        document.add(title);

        // Información comprimida en tabla de una fila con múltiples columnas
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1.5f, 1.5f, 1.5f}))
                .useAllAvailableWidth()
                .setMarginBottom(10);

        // Color de fondo diferente si es S/N
        DeviceRgb backgroundColor = isSROrder ? new DeviceRgb(255, 229, 229) : (DeviceRgb) ColorConstants.WHITE;

        // Primera línea: N° Factura, Fecha, Estado, Vendedor
        addInfoCell(infoTable, "N° Factura:", order.getInvoiceNumber() != null ? order.getInvoiceNumber().toString() : "---", true, backgroundColor);
        addInfoCell(infoTable, "Fecha:", order.getFecha().format(DATE_FORMATTER), true, backgroundColor);
        addInfoCell(infoTable, "Estado:", order.getEstado().toString(), true, backgroundColor);
        addInfoCell(infoTable, "Vendedor:", order.getVendedor().getUsername(), true, backgroundColor);

        document.add(infoTable);

        // Segunda línea: Información del cliente (si existe)
        if (order.getCliente() != null) {
            Table clientTable = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1.5f, 1.5f, 1.5f}))
                    .useAllAvailableWidth()
                    .setMarginBottom(15);

            String telefono = order.getCliente().getTelefono() != null ? order.getCliente().getTelefono() : "---";
            String email = order.getCliente().getEmail() != null ? order.getCliente().getEmail() : "---";
            String direccion = order.getCliente().getDireccion() != null ? order.getCliente().getDireccion() : "---";

            addInfoCell(clientTable, "Cliente:", order.getCliente().getNombre(), true, backgroundColor);
            addInfoCell(clientTable, "Teléfono:", telefono, true, backgroundColor);
            addInfoCell(clientTable, "Email:", email, true, backgroundColor);
            addInfoCell(clientTable, "Dirección:", direccion, true, backgroundColor);

            document.add(clientTable);
        }
    }

    /**
     * Agregar celda de información con etiqueta y valor
     */
    private void addInfoCell(Table table, String label, String value, boolean isBold, DeviceRgb backgroundColor) {
        com.itextpdf.layout.element.Cell labelCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(label).setBold().setFontSize(9))
                .setBorder(null)
                .setPadding(2)
                .setBackgroundColor(backgroundColor)
                .setTextAlignment(TextAlignment.LEFT);
        table.addCell(labelCell);

        com.itextpdf.layout.element.Cell valueCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(value).setFontSize(9))
                .setBorder(null)
                .setPadding(2)
                .setBackgroundColor(backgroundColor)
                .setTextAlignment(TextAlignment.LEFT);
        table.addCell(valueCell);
    }

    private void addProductsTable(Document document, Order order, boolean isSROrder) {
        Paragraph productsTitle = new Paragraph("DETALLE DE PRODUCTOS")
                .setFontSize(14)
                .setBold()
                .setFontColor(isSROrder ? new DeviceRgb(220, 53, 69) : BRAND_COLOR)
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(productsTitle);

        Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 2, 2}))
                .useAllAvailableWidth();

        // Header con color según tipo de factura
        DeviceRgb headerColor = isSROrder ? new DeviceRgb(220, 53, 69) : BRAND_COLOR;
        addTableHeaderCell(table, "Producto", headerColor);
        addTableHeaderCell(table, "Cant.", headerColor);
        addTableHeaderCell(table, "P. Unitario", headerColor);
        addTableHeaderCell(table, "Subtotal", headerColor);

        // Items
        for (OrderItem item : order.getItems()) {
            addTableDataCell(table, item.getProduct().getNombre());
            addTableDataCell(table, String.valueOf(item.getCantidad()));
            addTableDataCell(table, formatCurrency(item.getPrecioUnitario()));
            addTableDataCell(table, formatCurrency(item.getSubTotal()));
        }

        document.add(table);
    }

    private void addTotals(Document document, Order order, boolean isSROrder) {
        Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .useAllAvailableWidth()
                .setMarginTop(15);

        // Colores según tipo de factura
        DeviceRgb totalColor = isSROrder ? new DeviceRgb(220, 53, 69) : BRAND_COLOR;
        DeviceRgb lightBg = isSROrder ? new DeviceRgb(255, 229, 229) : LIGHT_GRAY;

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
                .add(new Paragraph("TOTAL:").setFontSize(14).setBold().setFontColor(ColorConstants.WHITE))
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(null)
                .setBackgroundColor(totalColor)
                .setPadding(8);
        totalsTable.addCell(totalLabelCell);

        com.itextpdf.layout.element.Cell totalValueCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(formatCurrency(order.getTotal())).setFontSize(14).setBold().setFontColor(ColorConstants.WHITE))
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(null)
                .setBackgroundColor(totalColor)
                .setPadding(8);
        totalsTable.addCell(totalValueCell);

        document.add(totalsTable);
    }

    private void addNotes(Document document, Order order, boolean isSROrder) {
        Paragraph notesTitle = new Paragraph("NOTAS")
                .setFontSize(12)
                .setBold()
                .setFontColor(isSROrder ? new DeviceRgb(220, 53, 69) : BRAND_COLOR)
                .setMarginTop(15)
                .setMarginBottom(5);
        document.add(notesTitle);

        DeviceRgb noteBg = isSROrder ? new DeviceRgb(255, 229, 229) : LIGHT_GRAY;
        Paragraph notesContent = new Paragraph(order.getNotas())
                .setFontSize(10)
                .setItalic()
                .setBackgroundColor(noteBg)
                .setPadding(10);
        document.add(notesContent);
    }

    private void addFooter(Document document, boolean isSROrder) {
        document.add(new Paragraph("\n"));

        SolidLine lineDrawer = new SolidLine();
        lineDrawer.setColor(isSROrder ? new DeviceRgb(220, 53, 69) : ColorConstants.LIGHT_GRAY);
        lineDrawer.setLineWidth(isSROrder ? 2f : 1f);
        LineSeparator separator = new LineSeparator(lineDrawer);
        document.add(separator);

        Paragraph footer = new Paragraph("Gracias por su compra - VITALEXA")
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10)
                .setFontColor(isSROrder ? new DeviceRgb(220, 53, 69) : ColorConstants.BLACK);
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

    private void addTableHeaderCell(Table table, String content, DeviceRgb backgroundColor) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(content).setBold().setFontSize(11))
                .setBackgroundColor(backgroundColor)
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
