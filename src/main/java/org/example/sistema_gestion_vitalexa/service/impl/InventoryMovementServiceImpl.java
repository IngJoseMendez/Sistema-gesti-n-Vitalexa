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
import org.example.sistema_gestion_vitalexa.entity.InventoryMovement;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.InventoryMovementRepository;
import org.example.sistema_gestion_vitalexa.service.InventoryMovementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryMovementServiceImpl implements InventoryMovementService {

    private final InventoryMovementRepository repository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DeviceRgb BRAND_COLOR = new DeviceRgb(52, 73, 94);
    private static final DeviceRgb HEADER_BG_COLOR = new DeviceRgb(240, 240, 240);

    @Override
    @Transactional
    public void logMovement(Product product, InventoryMovementType type, Integer quantity, Integer previousStock,
            Integer newStock, String reason, String username) {
        try {
            InventoryMovement movement = InventoryMovement.builder()
                    .product(product)
                    .productName(product.getNombre()) // Backup name
                    .type(type)
                    .quantity(quantity)
                    .previousStock(previousStock)
                    .newStock(newStock)
                    .reason(reason)
                    .username(username != null ? username : "System")
                    .build();

            repository.save(movement);
            log.info("Inventory movement logged: {} - Product: {} ({})", type, product.getNombre(), quantity);
        } catch (Exception e) {
            log.error("Failed to log inventory movement", e);
            // Don't throw exception to avoid rolling back the main transaction?
            // Better to log error but let the transaction continue or fail?
            // Usually audit should be critical, but strict requirement might vary.
            // We'll let it fail if it can't save to ensure data consistency of audit.
            throw e;
        }
    }

    @Override
    public Page<InventoryMovement> getHistory(UUID productId, InventoryMovementType type, LocalDateTime startDate,
            LocalDateTime endDate, Pageable pageable) {
        return repository.findWithFilters(productId, type, startDate, endDate, pageable);
    }

    @Override
    public List<InventoryMovement> getAllHistory(UUID productId, InventoryMovementType type, LocalDateTime startDate,
            LocalDateTime endDate) {
        return repository.findAllWithFilters(productId, type, startDate, endDate);
    }

    @Override
    public InventoryMovement findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Movimiento no encontrado"));
    }

    @Override
    public byte[] generateHistoryPdf(List<InventoryMovement> movements, String username, String filterDescription) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // 1. Header
            addHeader(document, username, filterDescription);

            // 2. Summary
            addSummary(document, movements);

            // 3. Table
            addMovementsTable(document, movements);

            // 4. Footer
            addFooter(document);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating inventory history PDF", e);
            throw new RuntimeException("Error generating history PDF", e);
        }
    }

    // --- PDF Helpers ---

    private void addHeader(Document document, String username, String filterDescription) {
        try {
            ImageData imageData = ImageDataFactory.create("src/main/resources/static/images/logo.png");
            Image logo = new Image(imageData);
            logo.setWidth(120);
            logo.setHorizontalAlignment(HorizontalAlignment.LEFT);
            document.add(logo);
        } catch (Exception e) {
        }

        Paragraph title = new Paragraph("HISTORIAL DE MOVIMIENTOS DE INVENTARIO")
                .setFontSize(16)
                .setBold()
                .setFontColor(BRAND_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10)
                .setMarginBottom(10);
        document.add(title);

        Paragraph desc = new Paragraph(filterDescription)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(desc);

        Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 1, 3 }))
                .useAllAvailableWidth()
                .setMarginBottom(15);

        addInfoRow(infoTable, "Fecha de Reporte:", LocalDateTime.now().format(DATE_FORMATTER));
        addInfoRow(infoTable, "Generado por:", username != null ? username : "N/A");

        document.add(infoTable);
    }

    private void addSummary(Document document, List<InventoryMovement> movements) {
        long totalMoves = movements.size();
        long entries = movements.stream().filter(m -> isPositiveMove(m.getType())).count();
        long exits = movements.stream()
                .filter(m -> !isPositiveMove(m.getType()) && m.getType() != InventoryMovementType.UPDATE).count();

        Table summaryTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1, 1 }))
                .useAllAvailableWidth()
                .setMarginBottom(20);

        addHeaderCell(summaryTable, "Total Movimientos");
        addHeaderCell(summaryTable, "Entradas (Creación/Restock)");
        addHeaderCell(summaryTable, "Salidas (Ventas/Eliminación)");

        addCell(summaryTable, String.valueOf(totalMoves), TextAlignment.CENTER);
        addCell(summaryTable, String.valueOf(entries), TextAlignment.CENTER);
        addCell(summaryTable, String.valueOf(exits), TextAlignment.CENTER);

        document.add(summaryTable);
    }

    private boolean isPositiveMove(InventoryMovementType type) {
        return type == InventoryMovementType.CREATION || type == InventoryMovementType.RESTOCK
                || type == InventoryMovementType.RETURN;
    }

    private void addMovementsTable(Document document, List<InventoryMovement> movements) {
        Table table = new Table(UnitValue.createPercentArray(new float[] { 2, 2, 3, 1.5f, 1, 1, 1, 2 }))
                .useAllAvailableWidth()
                .setFontSize(8);

        // Headers
        addHeaderCell(table, "Fecha");
        addHeaderCell(table, "Producto");
        addHeaderCell(table, "Tipo / Razón");
        addHeaderCell(table, "Usuario");
        addHeaderCell(table, "Previo");
        addHeaderCell(table, "Cant.");
        addHeaderCell(table, "Nuevo");
        addHeaderCell(table, "ID Movimiento");

        for (InventoryMovement m : movements) {
            addCell(table, m.getTimestamp().format(DATE_FORMATTER), TextAlignment.LEFT);
            addCell(table, m.getProductName(), TextAlignment.LEFT);
            addCell(table, m.getType() + "\n" + (m.getReason() != null ? m.getReason() : ""), TextAlignment.LEFT);
            addCell(table, m.getUsername(), TextAlignment.LEFT);
            addCell(table, String.valueOf(m.getPreviousStock()), TextAlignment.CENTER);

            // Highlight quantity (+/-)
            String sign = isPositiveMove(m.getType()) ? "+" : "-";
            if (m.getType() == InventoryMovementType.UPDATE)
                sign = "~"; // Update might not change stock
            if (m.getType() == InventoryMovementType.STOCK_ADJUSTMENT)
                sign = m.getNewStock() > m.getPreviousStock() ? "+" : "-";

            addCell(table, sign + m.getQuantity(), TextAlignment.CENTER);
            addCell(table, String.valueOf(m.getNewStock()), TextAlignment.CENTER);
            addCell(table, m.getId().toString().substring(0, 8), TextAlignment.CENTER);
        }

        document.add(table);
    }

    private void addFooter(Document document) {
        Paragraph footer = new Paragraph("\nReporte generado por Vitalexa. Documento de uso interno.")
                .setFontSize(8)
                .setItalic()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginTop(20);
        document.add(footer);
    }

    // --- Table Helpers ---
    private void addInfoRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold().setFontSize(9)).setBorder(null));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(9)).setBorder(null));
    }

    private void addHeaderCell(Table table, String text) {
        table.addCell(new Cell()
                .add(new Paragraph(text).setBold().setFontSize(8))
                .setBackgroundColor(HEADER_BG_COLOR)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private void addCell(Table table, String text, TextAlignment alignment) {
        table.addCell(new Cell()
                .add(new Paragraph(text != null ? text : "").setFontSize(8))
                .setTextAlignment(alignment));
    }
}
