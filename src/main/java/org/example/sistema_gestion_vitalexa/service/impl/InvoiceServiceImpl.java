package org.example.sistema_gestion_vitalexa.service.impl;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine; // ✅ CAMBIO AQUÍ
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
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

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

                        // Obtener orden completa CON promociones (EAGER loading)
                        Order order = ordenRepository.findByIdWithPromotions(orderId)
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

                        // ===== FOOTER =====
                        addFooter(document, order, isSROrder);

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

                // Watermark para facturas S/R -> REMOVIDO: Se usa estilo estándar

                SolidLine lineDrawer = new SolidLine();
                lineDrawer.setColor(BRAND_COLOR);
                lineDrawer.setLineWidth(2f);
                LineSeparator separator = new LineSeparator(lineDrawer);
                document.add(separator);
                document.add(new Paragraph("\n"));
        }

        private void addOrderInfo(Document document, Order order, boolean isSROrder) {
                // Indicador S/N -> REMOVIDO: Se usa estilo estándar

                // Título con color estándar
                Paragraph title = new Paragraph("FACTURA DE PEDIDO")
                                .setFontSize(18)
                                .setBold()
                                .setTextAlignment(TextAlignment.CENTER)
                                .setFontColor(BRAND_COLOR)
                                .setMarginBottom(15);
                document.add(title);

                // Información comprimida en tabla de una fila con múltiples columnas
                Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 1.5f, 1.5f, 1.5f, 1.5f }))
                                .useAllAvailableWidth()
                                .setMarginBottom(10);

                // Color de fondo estándar
                DeviceRgb backgroundColor = (DeviceRgb) ColorConstants.WHITE;

                // Primera línea: N° Factura, Fecha, Estado, Vendedor
                addInfoCell(infoTable, "N° Factura:",
                                order.getInvoiceNumber() != null ? order.getInvoiceNumber().toString() : "---", true,
                                backgroundColor);
                addInfoCell(infoTable, "Fecha:", order.getFecha().format(DATE_FORMATTER), true, backgroundColor);
                addInfoCell(infoTable, "Estado:", order.getEstado().toString(), true, backgroundColor);
                addInfoCell(infoTable, "Vendedor:", order.getVendedor().getUsername(), true, backgroundColor);

                document.add(infoTable);

                // Segunda línea: Información del cliente (si existe)
                if (order.getCliente() != null) {
                        Table clientTable = new Table(
                                        UnitValue.createPercentArray(new float[] { 1.5f, 1.5f, 1.5f, 1.5f }))
                                        .useAllAvailableWidth()
                                        .setMarginBottom(15);

                        String telefono = order.getCliente().getTelefono() != null ? order.getCliente().getTelefono()
                                        : "---";
                        String email = order.getCliente().getEmail() != null ? order.getCliente().getEmail() : "---";
                        String direccion = order.getCliente().getDireccion() != null ? order.getCliente().getDireccion()
                                        : "---";

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

        // ... (imports existentes)

        private void addProductsTable(Document document, Order order, boolean isSROrder) {
                Paragraph productsTitle = new Paragraph("DETALLE DE PRODUCTOS")
                                .setFontSize(14)
                                .setBold()
                                .setFontColor(BRAND_COLOR)
                                .setMarginTop(10)
                                .setMarginBottom(5);
                document.add(productsTitle);

                Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 2, 2 }))
                                .useAllAvailableWidth();

                // Header
                DeviceRgb headerColor = BRAND_COLOR;
                addTableHeaderCell(table, "Producto", headerColor);
                addTableHeaderCell(table, "Cant.", headerColor);
                addTableHeaderCell(table, "P. Unitario", headerColor);
                addTableHeaderCell(table, "Subtotal", headerColor);

                // 1. Separar items: Sin promoción vs Con promoción
                List<OrderItem> regularItems = new ArrayList<>();
                // ✅ ACTUALIZADO: Agrupar por promotionInstanceId (no promotion.id)
                // Esto permite tener múltiples instancias de la misma promoción como grupos separados
                Map<String, List<OrderItem>> itemsByPromotion = new java.util.HashMap<>();

                for (OrderItem item : order.getItems()) {
                        // Ignorar items de flete (se muestran en totales)
                        if (Boolean.TRUE.equals(item.getIsFreightItem())) {
                                continue;
                        }

                        if (item.getPromotion() == null) {
                                regularItems.add(item);
                        } else {
                                // ✅ NUEVO: Usar promotionInstanceId si está disponible, fallback a promotion.id
                                String promoKey = item.getPromotionInstanceId() != null
                                    ? item.getPromotionInstanceId().toString()
                                    : item.getPromotion().getId().toString();
                                itemsByPromotion.computeIfAbsent(promoKey, k -> new ArrayList<>()).add(item);
                        }
                }

                // 2. Agregar items regulares primero
                // Ordenar: Pagados primero, Bonificados al final
                regularItems.sort(Comparator.comparing((OrderItem item) -> Boolean.TRUE.equals(item.getIsBonified())));

                for (OrderItem item : regularItems) {
                        if (Boolean.TRUE.equals(item.getIsBonified())) {
                                addFreeItemRow(table, item);
                        } else {
                                addItemRow(table, item);
                        }
                }

                // 3. Agregar bloques de promociones
                if (!itemsByPromotion.isEmpty()) {
                        // Recorremos las promociones agrupadas
                        for (Map.Entry<String, List<OrderItem>> entry : itemsByPromotion.entrySet()) {
                                List<OrderItem> promoItems = entry.getValue();
                                // Tomamos la información de la promoción del primer item (todos comparten la
                                // misma promoción)
                                var promo = promoItems.get(0).getPromotion();

                                // Construir encabezado con nombre y precio de la promoción
                                String promoHeaderText = "PROMOCIÓN: " + promo.getNombre();
                                if (promo.getPackPrice() != null) {
                                        promoHeaderText += " - Precio: $" + promo.getPackPrice().toPlainString();
                                }

                                // Separador de promoción
                                com.itextpdf.layout.element.Cell promoHeader = new com.itextpdf.layout.element.Cell(1,
                                                4)
                                                .add(new Paragraph(promoHeaderText)
                                                                .setBold()
                                                                .setFontColor(ColorConstants.WHITE)
                                                                .setBackgroundColor(new DeviceRgb(100, 149, 237)) // Cornflower
                                                                                                                  // Blue
                                                                .setPadding(5)
                                                                .setTextAlignment(TextAlignment.LEFT));
                                table.addCell(promoHeader);

                                // Listar items de la promoción
                                // Primero los pagados/principales
                                promoItems.stream()
                                                .filter(i -> !Boolean.TRUE.equals(i.getIsFreeItem()))
                                                .forEach(item -> addItemRow(table, item));

                                // Luego los gratis/bonificados
                                promoItems.stream()
                                                .filter(i -> Boolean.TRUE.equals(i.getIsFreeItem()))
                                                .forEach(item -> addFreeItemRow(table, item));
                        }
                }

                document.add(table);
        }

        private void addItemRow(Table table, OrderItem item) {
                String productName = item.getProduct().getNombre();
                // Marcar Productos Sin Stock -> REMOVIDO POR SOLICITUD DEL USUARIO
                // ya que la factura es de empaquetado y si se pidió es porque se va a enviar
                /*
                 * if (Boolean.TRUE.equals(item.getOutOfStock())) {
                 * productName += " [SIN STOCK]";
                 * }
                 */

                addTableDataCell(table, productName);
                addTableDataCell(table, String.valueOf(item.getCantidad()));
                if (Boolean.TRUE.equals(item.getIsPromotionItem())) {
                        addTableDataCell(table, "");
                        addTableDataCell(table, "");
                } else {
                        addTableDataCell(table, formatCurrency(item.getPrecioUnitario()));
                        addTableDataCell(table, formatCurrency(item.getSubTotal()));
                }
        }

        private void addFreeItemRow(Table table, OrderItem item) {
                String productName = item.getProduct().getNombre() + " (BONIFICADO)";
                // Marcar Productos Sin Stock -> REMOVIDO
                /*
                 * if (Boolean.TRUE.equals(item.getOutOfStock())) {
                 * productName += " [SIN STOCK]";
                 * }
                 */

                com.itextpdf.layout.element.Cell nameCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph(productName)
                                                .setFontSize(10)
                                                .setItalic()
                                                .setFontColor(new DeviceRgb(40, 167, 69))) // Verde
                                .setPadding(6)
                                .setTextAlignment(TextAlignment.CENTER);
                table.addCell(nameCell);

                addTableDataCell(table, String.valueOf(item.getCantidad()));

                // Precio y Subtotal $0.00
                addTableDataCell(table, "$0.00");
                addTableDataCell(table, "$0.00");
        }

        private void addTotals(Document document, Order order, boolean isSROrder) {
                Table totalsTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1 }))
                                .useAllAvailableWidth()
                                .setMarginTop(15);

                // Colores estándar
                DeviceRgb totalColor = BRAND_COLOR;
                DeviceRgb lightBg = LIGHT_GRAY;

                // DETECTAR si es orden de promoción
                // Subtotal: Suma de todos los items que NO son flete
                // Esto maneja correctamente múltiples promociones y productos sueltos
                BigDecimal subtotalAmount = order.getItems().stream()
                                .filter(item -> !Boolean.TRUE.equals(item.getIsFreightItem()))
                                .map(OrderItem::getSubTotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                com.itextpdf.layout.element.Cell subtotalLabelCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph("SUBTOTAL:").setBold())
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setBorder(null)
                                .setPadding(5);
                totalsTable.addCell(subtotalLabelCell);

                com.itextpdf.layout.element.Cell subtotalValueCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph(formatCurrency(subtotalAmount)))
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setBorder(null)
                                .setPadding(5);
                totalsTable.addCell(subtotalValueCell);

                // Mostrar descuento si fue aplicado
                BigDecimal discountPercentage = order.getDiscountPercentage();
                BigDecimal discountAmount = BigDecimal.ZERO;
                if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
                        // Calcular monto del descuento basado en el subtotal correcto
                        discountAmount = subtotalAmount
                                        .multiply(discountPercentage)
                                        .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);

                        com.itextpdf.layout.element.Cell discountLabelCell = new com.itextpdf.layout.element.Cell()
                                        .add(new Paragraph("DESCUENTO (" + discountPercentage + "%):").setBold()
                                                        .setFontColor(new DeviceRgb(40, 167, 69))) // Verde
                                        .setTextAlignment(TextAlignment.RIGHT)
                                        .setBorder(null)
                                        .setPadding(5);
                        totalsTable.addCell(discountLabelCell);

                        com.itextpdf.layout.element.Cell discountValueCell = new com.itextpdf.layout.element.Cell()
                                        .add(new Paragraph("-" + formatCurrency(discountAmount))
                                                        .setFontColor(new DeviceRgb(40, 167, 69))) // Verde
                                        .setTextAlignment(TextAlignment.RIGHT)
                                        .setBorder(null)
                                        .setPadding(5);
                        totalsTable.addCell(discountValueCell);
                }

                // Mostrar Flete si aplica
                BigDecimal freightAmount = BigDecimal.ZERO;
                if (Boolean.TRUE.equals(order.getIncludeFreight())) {
                        boolean isBonified = Boolean.TRUE.equals(order.getIsFreightBonified());
                        freightAmount = isBonified ? BigDecimal.ZERO : new BigDecimal("15000"); // Valor fijo del flete

                        String freightLabel = "FLETE:";
                        if (order.getFreightCustomText() != null && !order.getFreightCustomText().isBlank()) {
                                freightLabel = order.getFreightCustomText();
                        }

                        // Añadir cantidad si es mayor a 1
                        if (order.getFreightQuantity() != null && order.getFreightQuantity() > 1) {
                                freightLabel += " (x" + order.getFreightQuantity() + ")";
                        }

                        com.itextpdf.layout.element.Cell freightLabelCell = new com.itextpdf.layout.element.Cell()
                                        .add(new Paragraph(freightLabel).setBold())
                                        .setTextAlignment(TextAlignment.RIGHT)
                                        .setBorder(null)
                                        .setPadding(5);
                        totalsTable.addCell(freightLabelCell);

                        com.itextpdf.layout.element.Cell freightValueCell = new com.itextpdf.layout.element.Cell()
                                        .add(new Paragraph(formatCurrency(freightAmount)))
                                        .setTextAlignment(TextAlignment.RIGHT)
                                        .setBorder(null)
                                        .setPadding(5);
                        totalsTable.addCell(freightValueCell);

                        // LISTAR FREIGHT ITEMS (Items incluidos en el flete)
                        List<OrderItem> freightItems = order.getItems().stream()
                                        .filter(i -> Boolean.TRUE.equals(i.getIsFreightItem()))
                                        .collect(Collectors.toList());

                        if (!freightItems.isEmpty()) {
                                for (OrderItem fi : freightItems) {
                                        String itemDesc = " - Incluye: " + fi.getProduct().getNombre() + " x"
                                                        + fi.getCantidad();

                                        com.itextpdf.layout.element.Cell itemLabelCell = new com.itextpdf.layout.element.Cell()
                                                        .add(new Paragraph(itemDesc).setFontSize(8).setItalic()
                                                                        .setFontColor(ColorConstants.GRAY))
                                                        .setTextAlignment(TextAlignment.RIGHT)
                                                        .setBorder(null)
                                                        .setPadding(0)
                                                        .setPaddingRight(5);
                                        totalsTable.addCell(itemLabelCell);

                                        com.itextpdf.layout.element.Cell itemValueCell = new com.itextpdf.layout.element.Cell()
                                                        .add(new Paragraph(""))
                                                        .setBorder(null);
                                        totalsTable.addCell(itemValueCell);
                                }
                        }
                }

                // Total final = (Subtotal - Descuento) + Flete
                BigDecimal baseTotal = subtotalAmount.subtract(discountAmount);
                BigDecimal finalTotal = baseTotal.add(freightAmount);

                com.itextpdf.layout.element.Cell totalLabelCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph("TOTAL:").setFontSize(14).setBold()
                                                .setFontColor(ColorConstants.WHITE))
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setBorder(null)
                                .setBackgroundColor(totalColor)
                                .setPadding(8);
                totalsTable.addCell(totalLabelCell);

                com.itextpdf.layout.element.Cell totalValueCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph(formatCurrency(finalTotal)).setFontSize(14).setBold()
                                                .setFontColor(ColorConstants.WHITE))
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
                                .setFontColor(BRAND_COLOR)
                                .setMarginTop(15)
                                .setMarginBottom(5);
                document.add(notesTitle);

                DeviceRgb noteBg = LIGHT_GRAY;
                Paragraph notesContent = new Paragraph(order.getNotas())
                                .setFontSize(10)
                                .setItalic()
                                .setBackgroundColor(noteBg)
                                .setPadding(10);
                document.add(notesContent);
        }

        private void addFooter(Document document, Order order, boolean isSROrder) {
                document.add(new Paragraph("\n"));

                SolidLine lineDrawer = new SolidLine();
                lineDrawer.setColor(ColorConstants.LIGHT_GRAY);
                lineDrawer.setLineWidth(1f);
                LineSeparator separator = new LineSeparator(lineDrawer);
                document.add(separator);

                // Solo mostrar agradecimiento y contacto si NO es S/R
                if (!isSROrder) {
                        Paragraph footer = new Paragraph("Gracias por su compra - VITALEXA")
                                        .setFontSize(10)
                                        .setTextAlignment(TextAlignment.CENTER)
                                        .setMarginTop(10)
                                        .setFontColor(ColorConstants.BLACK);
                        document.add(footer);

                        Paragraph contact = new Paragraph("Contacto: vitalexadistribuidora@gmail.com | Tel: 3122112815")
                                        .setFontSize(8)
                                        .setTextAlignment(TextAlignment.CENTER)
                                        .setFontColor(ColorConstants.GRAY);
                        document.add(contact);
                }

                // MENSAJES DE CONSIGNACIÓN
                // Detectar si es orden de promoción
                boolean isPromoOrder = (order.getNotas() != null && order.getNotas().contains("[Promoción]")) ||
                                (order.getItems() != null
                                                && order.getItems().stream().anyMatch(i -> i.getPromotion() != null));

                Paragraph consignment = new Paragraph()
                                .setFontSize(9)
                                .setBold()
                                .setTextAlignment(TextAlignment.CENTER)
                                .setMarginTop(10);

                if (isPromoOrder) {
                        consignment.add("FAVOR CONSIGNAR A: BANCOLOMBIA cuenta de Ahorros No 779-424-507-13");
                } else {
                        consignment.add("FAVOR CONSIGNAR A: BANCOLOMBIA cuenta de Ahorros No 916-907-985-10 - NEQUI / DAVIPLATA 310 489 1636");
                }

                document.add(consignment);
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
