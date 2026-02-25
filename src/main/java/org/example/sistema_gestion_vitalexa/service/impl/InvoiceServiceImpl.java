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
import org.example.sistema_gestion_vitalexa.entity.SpecialPromotion;
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
                java.time.LocalDateTime displayDate = order.getCompletedAt() != null ? order.getCompletedAt()
                                : order.getFecha();
                addInfoCell(infoTable, "Fecha:", displayDate.format(DATE_FORMATTER), true, backgroundColor);
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
                        String nit = order.getCliente().getNit() != null ? order.getCliente().getNit() : "---";
                        String representanteLegal = order.getCliente().getRepresentanteLegal() != null
                                        ? order.getCliente().getRepresentanteLegal()
                                        : "---";

                        addInfoCell(clientTable, "Cliente:", order.getCliente().getNombre(), true, backgroundColor);
                        addInfoCell(clientTable, "NIT:", nit, true, backgroundColor);
                        addInfoCell(clientTable, "Teléfono:", telefono, true, backgroundColor);
                        addInfoCell(clientTable, "Email:", email, true, backgroundColor);

                        document.add(clientTable);

                        // Fila para Representante Legal
                        Table repTable = new Table(
                                        UnitValue.createPercentArray(new float[] { 1.5f, 1.5f, 1.5f, 1.5f }))
                                        .useAllAvailableWidth()
                                        .setMarginBottom(5);

                        addInfoCell(repTable, "Representante Legal:", representanteLegal, true, backgroundColor);
                        addInfoCell(repTable, "", "", false, backgroundColor);
                        addInfoCell(repTable, "", "", false, backgroundColor);
                        addInfoCell(repTable, "", "", false, backgroundColor);

                        document.add(repTable);

                        // Tercera línea: Dirección (ocupa toda la fila)
                        Table addressTable = new Table(UnitValue.createPercentArray(new float[] { 1f }))
                                        .useAllAvailableWidth()
                                        .setMarginBottom(15);

                        addInfoCell(addressTable, "Dirección:", direccion, true, backgroundColor);

                        document.add(addressTable);
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
                // ✅ AGRUPADO POR promotion.id / specialPromotion.id para compactar en la factura
                // Múltiples instancias de la MISMA promoción se combinan en una sola fila
                // La clave es el ID de la promoción lógica (no el instanceId)
                Map<String, List<OrderItem>> itemsByPromotion = new java.util.LinkedHashMap<>();

                for (OrderItem item : order.getItems()) {
                        // Ignorar items de flete (se muestran en totales)
                        if (Boolean.TRUE.equals(item.getIsFreightItem())) {
                                continue;
                        }

                        if (item.getPromotion() == null) {
                                regularItems.add(item);
                        } else {
                                // ✅ AGRUPAR por la ID lógica de la promoción (no por instanceId)
                                // Así todas las instancias de "Promo X" quedan en un solo grupo
                                String promoKey = item.getSpecialPromotion() != null
                                                ? "sp_" + item.getSpecialPromotion().getId().toString()
                                                : "p_" + item.getPromotion().getId().toString();
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

                // 3. Agregar bloques de promociones - AGRUPADAS (una fila por tipo de promo)
                if (!itemsByPromotion.isEmpty()) {
                        for (Map.Entry<String, List<OrderItem>> entry : itemsByPromotion.entrySet()) {
                                List<OrderItem> promoItems = entry.getValue();
                                // Tomamos la información de la promoción del primer item
                                var promo = promoItems.get(0).getPromotion();

                                // Detectar si es una SpecialPromotion
                                SpecialPromotion specialPromo = promoItems.stream()
                                                .map(OrderItem::getSpecialPromotion)
                                                .filter(Objects::nonNull)
                                                .findFirst()
                                                .orElse(null);

                                String promoName = (specialPromo != null) ? specialPromo.getNombre()
                                                : promo.getNombre();
                                BigDecimal promoUnitPrice = (specialPromo != null && specialPromo.getPackPrice() != null)
                                                ? specialPromo.getPackPrice()
                                                : promo.getPackPrice();

                                // ✅ Contar cuántas INSTANCIAS hay de esta promoción
                                // Una instancia = un grupo de items con el mismo promotionInstanceId
                                long instanceCount = promoItems.stream()
                                                .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem())
                                                                && !Boolean.TRUE.equals(i.getIsFreeItem()))
                                                .map(OrderItem::getPromotionInstanceId)
                                                .filter(Objects::nonNull)
                                                .distinct()
                                                .count();
                                // Fallback: si no hay instanceId, contar items main (no free)
                                if (instanceCount == 0) {
                                        instanceCount = promoItems.stream()
                                                        .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem())
                                                                        && !Boolean.TRUE.equals(i.getIsFreeItem()))
                                                        .count();
                                        if (instanceCount == 0) instanceCount = 1;
                                }

                                // ✅ Si la promo NO tiene packPrice fijo (surtida / BUY_GET_FREE),
                                // calcular el precio de UNA instancia sumando los subTotals de los
                                // items mainProduct (no free) que pertenecen a la primera instancia.
                                if (promoUnitPrice == null) {
                                        // Agrupar items no-free por instanceId y sumar subTotal de la primera instancia
                                        java.util.Optional<UUID> firstInstance = promoItems.stream()
                                                        .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem())
                                                                        && !Boolean.TRUE.equals(i.getIsFreeItem())
                                                                        && i.getPromotionInstanceId() != null)
                                                        .map(OrderItem::getPromotionInstanceId)
                                                        .findFirst();

                                        if (firstInstance.isPresent()) {
                                                UUID firstId = firstInstance.get();
                                                promoUnitPrice = promoItems.stream()
                                                                .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem())
                                                                                && !Boolean.TRUE.equals(i.getIsFreeItem())
                                                                                && firstId.equals(i.getPromotionInstanceId()))
                                                                .map(OrderItem::getSubTotal)
                                                                .filter(Objects::nonNull)
                                                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        } else {
                                                // Último fallback: sumar todos los items no-free
                                                BigDecimal totalSum = promoItems.stream()
                                                                .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem())
                                                                                && !Boolean.TRUE.equals(i.getIsFreeItem()))
                                                                .map(OrderItem::getSubTotal)
                                                                .filter(Objects::nonNull)
                                                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                                                promoUnitPrice = instanceCount > 0
                                                                ? totalSum.divide(BigDecimal.valueOf(instanceCount),
                                                                                2, java.math.RoundingMode.HALF_UP)
                                                                : totalSum;
                                        }
                                        // Si sigue siendo cero, dejar null para no mostrar fila vacía con $0
                                        if (promoUnitPrice != null && promoUnitPrice.compareTo(BigDecimal.ZERO) == 0) {
                                                promoUnitPrice = null;
                                        }
                                }

                                // ✅ Calcular precio total = precio unitario × cantidad de instancias
                                BigDecimal totalPromoPrice = null;
                                if (promoUnitPrice != null && instanceCount > 0) {
                                        totalPromoPrice = promoUnitPrice.multiply(BigDecimal.valueOf(instanceCount));
                                }

                                // Fila del encabezado de la promoción con color azul
                                String promoRowName = "PROMOCIÓN: " + promoName;
                                com.itextpdf.layout.element.Cell promoHeader = new com.itextpdf.layout.element.Cell(1, 4)
                                                .add(new Paragraph(promoRowName)
                                                                .setBold()
                                                                .setFontSize(8)
                                                                .setFontColor(ColorConstants.WHITE)
                                                                .setMargin(0)
                                                                .setTextAlignment(TextAlignment.LEFT))
                                                .setPaddingTop(2)
                                                .setPaddingBottom(2)
                                                .setPaddingLeft(4)
                                                .setPaddingRight(4)
                                                .setBackgroundColor(new DeviceRgb(100, 149, 237));
                                table.addCell(promoHeader);

                                // ✅ NUEVA FILA: cantidad de instancias + precio unitario + total
                                if (promoUnitPrice != null) {
                                        addTableDataCell(table, "");
                                        addTableDataCell(table, instanceCount > 1 ? "x" + instanceCount : "x1");
                                        addTableDataCell(table, formatCurrency(promoUnitPrice));
                                        addTableDataCell(table,
                                                        totalPromoPrice != null ? formatCurrency(totalPromoPrice) : "");
                                }
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
                                                .setFontSize(9)
                                                .setItalic()
                                                .setFontColor(new DeviceRgb(40, 167, 69))) // Verde
                                .setPadding(3)
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
                                .add(new Paragraph(content).setBold().setFontSize(10))
                                .setBackgroundColor(backgroundColor)
                                .setFontColor(ColorConstants.WHITE)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setPadding(4);
                table.addCell(cell);
        }

        private void addTableDataCell(Table table, String content) {
                com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph(content).setFontSize(9))
                                .setPadding(3)
                                .setTextAlignment(TextAlignment.CENTER);
                table.addCell(cell);
        }

        private String formatCurrency(BigDecimal amount) {
                return String.format("$%,.2f", amount);
        }
}
