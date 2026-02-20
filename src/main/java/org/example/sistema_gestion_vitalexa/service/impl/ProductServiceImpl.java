package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.SpecialProduct;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.ProductMapper;
import org.example.sistema_gestion_vitalexa.repository.OrdenItemRepository;
import org.example.sistema_gestion_vitalexa.repository.ProductRepository;
import org.example.sistema_gestion_vitalexa.service.InventoryMovementService;
import org.example.sistema_gestion_vitalexa.service.NotificationService;
import org.example.sistema_gestion_vitalexa.service.ProductImageService;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.example.sistema_gestion_vitalexa.service.ProductTagService;
import org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final NotificationService notificationService;
    private final ProductTagService productTagService;
    private final ProductImageService imageService;
    private final InventoryMovementService movementService;
    private final org.example.sistema_gestion_vitalexa.repository.SpecialProductRepository specialProductRepository;
    private final OrdenItemRepository ordenItemRepository;

    // Helper para decodificar y guardar imagen Base64
    private String handleBase64Image(String base64Image, String originalFilename) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }
        try {
            // Remover prefijo data:image/...;base64, si existe
            String base64Data = base64Image;
            if (base64Image.contains(",")) {
                base64Data = base64Image.split(",")[1];
            }

            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
            return imageService.saveImage(imageBytes,
                    originalFilename != null ? originalFilename : "image.jpg");
        } catch (Exception e) {
            log.error("Error al procesar imagen Base64", e);
            // No fallamos la transacción completa por una imagen, pero logueamos
            return null;
        }
    }

    @Override
    public ProductResponse create(CreateProductRequest request) {
        Product product = mapper.toEntity(request);

        // Procesar imagen Base64 si existe (prioridad sobre imageUrl existente si se
        // envía)
        if (request.imageBase64() != null && !request.imageBase64().isBlank()) {
            String savedUrl = handleBase64Image(request.imageBase64(), request.imageFileName());
            if (savedUrl != null) {
                product.setImageUrl(savedUrl);
            }
        }

        // Asignar tag si se proporciona
        if (request.tagId() != null) {
            product.setTag(productTagService.findEntityById(request.tagId()));
        }

        Product saved = repository.save(product);

        // LOG MOVEMENT
        movementService.logMovement(saved, InventoryMovementType.CREATION, saved.getStock(), 0, saved.getStock(),
                "Creación inicial", null);

        // NOTIFICAR CREACIÓN
        notificationService.sendInventoryUpdate(saved.getId().toString(), "PRODUCT_CREATED");

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public List<ProductResponse> createBulk(List<CreateProductRequest> requests) {
        return requests.stream()
                .map(this::create)
                .toList();
    }

    @Override
    @Transactional
    public BulkProductUpdateResult updateBulk(List<UpdateProductBulkRequest> requests) {
        java.util.List<ProductResponse> successfulUpdates = new java.util.ArrayList<>();
        java.util.List<BulkProductUpdateResult.BulkError> failures = new java.util.ArrayList<>();

        for (UpdateProductBulkRequest req : requests) {
            String productName = "ID: " + req.id();
            try {
                // 1. Intentar buscar en productos normales
                java.util.Optional<Product> productOpt = repository.findById(req.id());

                if (productOpt.isEmpty()) {
                    // 2. Si no es normal, verificar si es Especial
                    if (specialProductRepository.existsById(req.id())) {
                        log.info("Saltando actualización masiva para Producto Especial ID: {}", req.id());
                        continue; // SALTAR SILENCIOSAMENTE
                    }
                    // 3. Si no es ninguno, lanzar error
                    throw new BusinessExeption("Producto no encontrado: " + req.id());
                }

                Product product = productOpt.get();
                productName = product.getNombre();

                // Capture old state for logging
                Integer oldStock = product.getStock();
                boolean stockChanged = false;

                mapper.updateEntity(req, product);

                if (req.stock() != null && !req.stock().equals(oldStock)) {
                    stockChanged = true;
                }

                // Procesar imagen Base64
                if (req.imageBase64() != null && !req.imageBase64().isBlank()) {
                    String savedUrl = handleBase64Image(req.imageBase64(), req.imageFileName());
                    if (savedUrl != null) {
                        product.setImageUrl(savedUrl);
                    }
                }

                if (req.tagId() != null) {
                    product.setTag(productTagService.findEntityById(req.tagId()));
                }

                checkStockLevels(product); // Verificar niveles de stock individualmente

                Product saved = repository.save(product);

                // LOG MOVEMENT
                if (stockChanged) {
                    int diff = Math.abs(saved.getStock() - oldStock);
                    movementService.logMovement(saved, InventoryMovementType.STOCK_ADJUSTMENT, diff, oldStock,
                            saved.getStock(), "Ajuste masivo de stock", null);
                } else {
                    movementService.logMovement(saved, InventoryMovementType.UPDATE, 0, oldStock, saved.getStock(),
                            "Actualización masiva de información", null);
                }

                successfulUpdates.add(mapper.toResponse(saved));

            } catch (BusinessExeption | jakarta.persistence.EntityNotFoundException e) {
                log.warn("Error actualizando producto en bulk (ID: {}): {}", req.id(), e.getMessage());
                failures.add(new BulkProductUpdateResult.BulkError(req.id(), productName, e.getMessage()));
            } catch (Exception e) {
                log.error("Error inesperado actualizando producto en bulk (ID: {})", req.id(), e);
                failures.add(new BulkProductUpdateResult.BulkError(req.id(), productName,
                        "Error inesperado: " + e.getMessage()));
            }
        }

        return new BulkProductUpdateResult(successfulUpdates, failures);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {

        Product product = repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));

        // Admin puede editar productos aunque estén desactivados

        // Capture old state for logging
        Integer oldStock = product.getStock();
        boolean stockChanged = false;

        // Actualizar solo los campos que no son null
        if (request.nombre() != null) {
            product.setNombre(request.nombre());
        }
        if (request.descripcion() != null) {
            product.setDescripcion(request.descripcion());
        }
        if (request.precio() != null) {
            product.setPrecio(request.precio());
        }
        if (request.stock() != null) {
            if (!request.stock().equals(oldStock)) {
                stockChanged = true;
            }
            product.setStock(request.stock());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }
        if (request.tagId() != null) {
            product.setTag(productTagService.findEntityById(request.tagId()));
        }

        Product updated = repository.save(product);

        // LOG MOVEMENT
        if (stockChanged) {
            // It was a stock adjustment (manual)
            int diff = Math.abs(updated.getStock() - oldStock);
            movementService.logMovement(updated, InventoryMovementType.STOCK_ADJUSTMENT, diff, oldStock,
                    updated.getStock(), "Ajuste manual de stock", null);
        } else {
            // General update
            movementService.logMovement(updated, InventoryMovementType.UPDATE, 0, oldStock, updated.getStock(),
                    "Actualización de información", null);
        }

        // VERIFICAR NIVELES DE STOCK
        checkStockLevels(updated);

        // NOTIFICAR ACTUALIZACIÓN
        notificationService.sendInventoryUpdate(updated.getId().toString(), "PRODUCT_UPDATED");

        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));

        log.info("Soft deleting producto ID: {}", id);

        Integer oldStock = product.getStock();

        product.setActive(false);
        repository.save(product);

        // LOG MOVEMENT
        movementService.logMovement(product, InventoryMovementType.DELETION, product.getStock(), oldStock,
                product.getStock(), "Eliminación Lógica (Soft Delete)", null);

        // NOTIFICAR ELIMINACIÓN
        notificationService.sendInventoryUpdate(id.toString(), "PRODUCT_DELETED");

        log.info("Producto eliminado (soft delete) correctamente: {}", id);
    }

    @Override
    @Transactional
    public void hardDelete(UUID id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));
        log.info("Hard deleting producto ID: {}", id);

        // LOG MOVEMENT (Before delete, using the entity data we have)
        movementService.logMovement(product, InventoryMovementType.DELETION, product.getStock(), product.getStock(), 0,
                "Eliminación Física (Hard Delete)", null);

        repository.deleteById(id);

        // NOTIFICAR ELIMINACIÓN
        notificationService.sendInventoryUpdate(id.toString(), "PRODUCT_DELETED");

        log.info("Producto eliminado físicamente: {}", id);
    }

    @Override
    public List<ProductResponse> findAllActive() {
        return repository.findByActiveTrue()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public Product findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));
    }

    @Override
    public ProductResponse findById(UUID id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Override
    @Transactional
    public void changeStatus(UUID productId, boolean activo) {
        Product product = repository.findById(productId)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));

        product.setActive(activo);
        repository.save(product);

        // NOTIFICAR CAMBIO DE ESTADO
        notificationService.sendInventoryUpdate(productId.toString(), "PRODUCT_STATUS_CHANGED");
    }

    @Override
    public List<ProductResponse> findAllAdmin() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<ProductResponse> findLowStock(int threshold) {
        return repository.findByStockLessThanAndActiveTrue(threshold)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Método auxiliar para verificar stock
    private void checkStockLevels(Product product) {
        if (product.getReorderPoint() != null && product.getReorderPoint() > 0) {
            if (product.getStock() == 0) {
                notificationService.sendOutOfStockAlert(
                        product.getId().toString(),
                        product.getNombre());
            } else if (product.getStock() <= product.getReorderPoint()) {
                notificationService.sendLowStockAlert(
                        product.getId().toString(),
                        product.getNombre(),
                        product.getStock(),
                        product.getReorderPoint());
            }
        }
    }

    @Override
    public Page<ProductResponse> findAllActive(Pageable pageable) {
        return repository.findByActiveTrue(pageable).map(mapper::toResponse);
    }

    @Override
    public Page<ProductResponse> findAllActiveInStock(Pageable pageable) {
        return repository.findByActiveTrueAndStockGreaterThan(0, pageable).map(mapper::toResponse);
    }

    @Override
    public Page<ProductResponse> searchActive(String q, Pageable pageable) {
        return repository.searchActive(q, pageable).map(mapper::toResponse);
    }

    @Override
    public Page<ProductResponse> findByTag(UUID tagId, Pageable pageable) {
        return repository.findByTagId(tagId, pageable).map(mapper::toResponse);
    }

    @Override
    public Page<ProductResponse> searchByTag(String q, UUID tagId, Pageable pageable) {
        return repository.searchByTagId(q, tagId, pageable).map(mapper::toResponse);
    }

    @Override
    public Product getSystemProductSurtido() {
        return repository.findByNombreAndIsHiddenTrue("SURTIDO PROMOCIONAL")
                .orElseThrow(() -> new BusinessExeption(
                        "Error del sistema: Producto 'SURTIDO PROMOCIONAL' no configurado."));
    }

    @Override
    @Transactional
    public org.example.sistema_gestion_vitalexa.entity.InventoryMovement addStock(UUID productId, int quantity,
            String reason, String username) {
        Product product;
        String logReason = reason;

        // 1. Intentar buscar producto normal
        java.util.Optional<Product> productOpt = repository.findById(productId);

        if (productOpt.isPresent()) {
            product = productOpt.get();
        } else {
            // 2. Si no es normal, buscar si es Especial
            SpecialProduct sp = specialProductRepository.findById(productId)
                    .orElseThrow(() -> new BusinessExeption("Producto no encontrado (ni regular ni especial)"));

            if (sp.isLinked()) {
                // Es un producto especial vinculado -> Actualizamos el PADRE
                product = sp.getParentProduct();
                logReason = (reason != null ? reason : "") + " (Vía Especial: " + sp.getNombre() + ")";
            } else {
                throw new BusinessExeption(
                        "No se puede agregar stock directamente a un producto especial independiente en este módulo. Use el producto padre o conviértalo.");
            }
        }

        if (quantity <= 0) {
            throw new BusinessExeption("La cantidad a agregar debe ser mayor a 0");
        }

        Integer oldStock = product.getStock();
        if (oldStock == null)
            oldStock = 0;

        product.setStock(oldStock + quantity);
        Product saved = repository.save(product);

        // LOG MOVEMENT and RETURN
        org.example.sistema_gestion_vitalexa.entity.InventoryMovement movement = movementService.logMovement(
                saved,
                InventoryMovementType.RESTOCK,
                quantity,
                oldStock,
                saved.getStock(),
                logReason != null && !logReason.isBlank() ? logReason : "Llegada de mercancía",
                username);

        // NOTIFICAR ACTUALIZACIÓN
        notificationService.sendInventoryUpdate(saved.getId().toString(), "PRODUCT_UPDATED");

        log.info("Stock agregado a producto {}: +{} (Nuevo stock: {}). Razón: {}",
                product.getNombre(), quantity, saved.getStock(), logReason);

        return movement;
    }

    @Override
    @Transactional
    public java.util.List<org.example.sistema_gestion_vitalexa.entity.InventoryMovement> addStockBulk(
            org.example.sistema_gestion_vitalexa.dto.BulkStockArrivalRequestDTO request,
            String username) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessExeption("La lista de productos no puede estar vacía");
        }

        String reason = request.reason() != null && !request.reason().isBlank()
                ? request.reason()
                : "Carga Masiva de Llegadas";

        java.util.List<org.example.sistema_gestion_vitalexa.entity.InventoryMovement> movements = new java.util.ArrayList<>();

        for (org.example.sistema_gestion_vitalexa.dto.StockArrivalItemDTO item : request.items()) {
            try {
                movements.add(addStock(item.productId(), item.quantity(), reason, username));
            } catch (Exception e) {
                log.error("Error agregando stock a producto {} en carga masiva: {}", item.productId(), e.getMessage());
                throw new BusinessExeption("Error al procesar producto ID " + item.productId() + ": " + e.getMessage());
            }
        }
        return movements;
    }

    @Override
    public byte[] exportInventoryToExcel() {
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Inventario");

            // ---- Estilos ----
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            org.apache.poi.ss.usermodel.CellStyle groupStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font groupFont = workbook.createFont();
            groupFont.setBold(true);
            groupFont.setFontHeightInPoints((short) 14);
            groupStyle.setFont(groupFont);
            groupStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            groupStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

            // Estilo alerta roja (stock negativo)
            org.apache.poi.ss.usermodel.CellStyle alertStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font alertFont = workbook.createFont();
            alertFont.setBold(true);
            alertFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
            alertStyle.setFont(alertFont);
            alertStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            org.apache.poi.ss.usermodel.CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            // ---- Encabezados ----
            // Columnas: #letra | Nombre | Precio | En Bodega | En Pedidos | Sistema | Activo
            String[] columns = { "#", "Nombre", "Precio", "En Bodega", "En Pedidos", "Sistema", "Activo" };
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // ---- Datos ----
            List<StockSummaryDTO> stockData = getStockReport();
            // Construir mapa productId -> StockSummaryDTO para lookup rápido
            java.util.Map<UUID, StockSummaryDTO> stockMap = new java.util.HashMap<>();
            for (StockSummaryDTO s : stockData) {
                stockMap.put(s.productId(), s);
            }

            List<ProductResponse> products = new java.util.ArrayList<>(findAllAdmin());
            products.sort((p1, p2) -> {
                String n1 = p1.nombre() != null ? p1.nombre() : "";
                String n2 = p2.nombre() != null ? p2.nombre() : "";
                return n1.compareToIgnoreCase(n2);
            });

            int rowIdx = 1;
            int startRow = 1;
            char currentLetter = '\0';

            for (int i = 0; i < products.size(); i++) {
                ProductResponse product = products.get(i);
                StockSummaryDTO stock = stockMap.get(product.id());

                int enBodega     = stock != null ? stock.stockFisicoReal() : (product.stock() != null ? product.stock() : 0);
                int enPedidos    = stock != null ? (stock.stockComprometido() != null ? stock.stockComprometido() : 0) : 0;
                int sistema      = stock != null ? (stock.stockEnBD() != null ? stock.stockEnBD() : 0) : (product.stock() != null ? product.stock() : 0);
                boolean alerta   = sistema < 0;

                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx);
                int colIdx = 0;

                // Columna letra grupo
                String name = product.nombre() != null ? product.nombre().trim() : "";
                char firstChar = name.isEmpty() ? '#' : Character.toUpperCase(name.charAt(0));

                if (i == 0) {
                    currentLetter = firstChar;
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(colIdx);
                    cell.setCellValue(String.valueOf(currentLetter));
                    cell.setCellStyle(groupStyle);
                } else if (firstChar != currentLetter) {
                    if (rowIdx - 1 > startRow) {
                        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(startRow, rowIdx - 1, 0, 0));
                    }
                    currentLetter = firstChar;
                    startRow = rowIdx;
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(colIdx);
                    cell.setCellValue(String.valueOf(currentLetter));
                    cell.setCellStyle(groupStyle);
                } else {
                    row.createCell(colIdx).setCellValue("");
                }
                colIdx++;

                row.createCell(colIdx++).setCellValue(product.nombre());
                row.createCell(colIdx++).setCellValue(product.precio() != null ? product.precio().doubleValue() : 0.0);

                org.apache.poi.ss.usermodel.Cell bodegaCell = row.createCell(colIdx++);
                bodegaCell.setCellValue(enBodega);
                bodegaCell.setCellStyle(centerStyle);

                org.apache.poi.ss.usermodel.Cell pedidosCell = row.createCell(colIdx++);
                pedidosCell.setCellValue(enPedidos);
                pedidosCell.setCellStyle(centerStyle);

                org.apache.poi.ss.usermodel.Cell sistemaCell = row.createCell(colIdx++);
                sistemaCell.setCellValue(sistema);
                sistemaCell.setCellStyle(alerta ? alertStyle : centerStyle);

                row.createCell(colIdx++).setCellValue(product.active() ? "Sí" : "No");

                rowIdx++;
            }

            // Merge último grupo
            if (rowIdx - 1 > startRow) {
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(startRow, rowIdx - 1, 0, 0));
            }

            // Leyenda debajo de la tabla
            rowIdx++;
            org.apache.poi.ss.usermodel.Row legendRow1 = sheet.createRow(rowIdx++);
            legendRow1.createCell(0).setCellValue("Leyenda:");
            org.apache.poi.ss.usermodel.Row legendRow2 = sheet.createRow(rowIdx++);
            legendRow2.createCell(0).setCellValue("En Bodega = unidades físicas reales en almacén");
            org.apache.poi.ss.usermodel.Row legendRow3 = sheet.createRow(rowIdx++);
            legendRow3.createCell(0).setCellValue("En Pedidos = unidades comprometidas en pedidos activos (pendientes de despacho)");
            org.apache.poi.ss.usermodel.Row legendRow4 = sheet.createRow(rowIdx);
            legendRow4.createCell(0).setCellValue("Sistema = stock registrado en BD (puede ser negativo si hay más pedidos que stock)");

            // Auto-size columnas
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            log.error("Error generando Excel de inventario", e);
            throw new BusinessExeption("Error generando el archivo de inventario: " + e.getMessage());
        }
    }

    @Override
    public byte[] exportInventoryToPdf() {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(out);
            com.itextpdf.kernel.pdf.PdfDocument pdf = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf,
                    com.itextpdf.kernel.geom.PageSize.A4.rotate());
            document.setMargins(20, 20, 20, 20);

            // Colores
            com.itextpdf.kernel.colors.DeviceRgb headerBg  = new com.itextpdf.kernel.colors.DeviceRgb(52, 73, 94);
            com.itextpdf.kernel.colors.DeviceRgb rowAlt     = new com.itextpdf.kernel.colors.DeviceRgb(245, 245, 245);
            com.itextpdf.kernel.colors.DeviceRgb alertRed   = new com.itextpdf.kernel.colors.DeviceRgb(231, 76, 60);
            com.itextpdf.kernel.colors.DeviceRgb alertRedBg = new com.itextpdf.kernel.colors.DeviceRgb(255, 235, 235);
            com.itextpdf.kernel.colors.DeviceRgb groupBg    = new com.itextpdf.kernel.colors.DeviceRgb(189, 195, 199);
            com.itextpdf.kernel.colors.Color    white       = com.itextpdf.kernel.colors.ColorConstants.WHITE;

            // ---- Título ----
            document.add(new com.itextpdf.layout.element.Paragraph("Reporte de Inventario — Vitalexa")
                    .setFontSize(18).setBold()
                    .setFontColor(headerBg)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    .setMarginBottom(4));

            document.add(new com.itextpdf.layout.element.Paragraph(
                    "Generado: " + java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                    .setFontSize(9)
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
                    .setMarginBottom(14));

            // ---- Tabla ----
            // Columnas: # | Nombre | Precio | En Bodega | En Pedidos | Sistema | Activo
            float[] colWidths = { 1f, 5f, 2f, 2f, 2f, 2f, 1.5f };
            com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(
                    com.itextpdf.layout.properties.UnitValue.createPercentArray(colWidths))
                    .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));

            String[] headers = { "#", "Nombre", "Precio", "En Bodega", "En Pedidos", "Sistema", "Activo" };
            for (String h : headers) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(h).setBold().setFontSize(9).setFontColor(white))
                        .setBackgroundColor(headerBg)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                        .setPadding(4));
            }

            // ---- Datos ----
            List<StockSummaryDTO> stockData = getStockReport();
            java.util.Map<UUID, StockSummaryDTO> stockMap = new java.util.HashMap<>();
            for (StockSummaryDTO s : stockData) {
                stockMap.put(s.productId(), s);
            }

            List<ProductResponse> products = new java.util.ArrayList<>(findAllAdmin());
            products.sort((p1, p2) -> {
                String n1 = p1.nombre() != null ? p1.nombre() : "";
                String n2 = p2.nombre() != null ? p2.nombre() : "";
                return n1.compareToIgnoreCase(n2);
            });

            java.util.Map<Character, Integer> groupCounts = new java.util.HashMap<>();
            for (ProductResponse p : products) {
                String nm = p.nombre() != null ? p.nombre().trim() : "";
                char fc = nm.isEmpty() ? '#' : Character.toUpperCase(nm.charAt(0));
                groupCounts.put(fc, groupCounts.getOrDefault(fc, 0) + 1);
            }

            char currentLetter = '\0';
            int rowNum = 0;

            for (ProductResponse product : products) {
                StockSummaryDTO stock = stockMap.get(product.id());

                int enBodega  = stock != null ? stock.stockFisicoReal() : (product.stock() != null ? product.stock() : 0);
                int enPedidos = stock != null && stock.stockComprometido() != null ? stock.stockComprometido() : 0;
                int sistema   = stock != null && stock.stockEnBD()         != null ? stock.stockEnBD()         : (product.stock() != null ? product.stock() : 0);
                boolean alerta = sistema < 0;

                String nm = product.nombre() != null ? product.nombre().trim() : "";
                char firstChar = nm.isEmpty() ? '#' : Character.toUpperCase(nm.charAt(0));

                // Celda de letra de grupo (con rowspan)
                if (firstChar != currentLetter) {
                    currentLetter = firstChar;
                    int span = groupCounts.getOrDefault(currentLetter, 1);
                    table.addCell(new com.itextpdf.layout.element.Cell(span, 1)
                            .add(new com.itextpdf.layout.element.Paragraph(String.valueOf(currentLetter))
                                    .setBold().setFontSize(13))
                            .setBackgroundColor(groupBg)
                            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                            .setPadding(3));
                }

                com.itextpdf.kernel.colors.Color rowBg = alerta ? alertRedBg : (rowNum % 2 == 0 ? white : rowAlt);
                rowNum++;

                // helper lambda para crear celda de datos
                java.util.function.BiFunction<String, com.itextpdf.layout.properties.TextAlignment, com.itextpdf.layout.element.Cell> mkCell =
                    (text, align) -> new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(text).setFontSize(9))
                        .setBackgroundColor(rowBg)
                        .setTextAlignment(align)
                        .setPadding(3);

                table.addCell(mkCell.apply(product.nombre() != null ? product.nombre() : "", com.itextpdf.layout.properties.TextAlignment.LEFT));
                table.addCell(mkCell.apply(String.format("$%.2f", product.precio() != null ? product.precio().doubleValue() : 0.0), com.itextpdf.layout.properties.TextAlignment.RIGHT));

                // En Bodega
                table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(String.valueOf(enBodega)).setFontSize(9).setBold())
                        .setBackgroundColor(rowBg)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER).setPadding(3));

                // En Pedidos
                table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(String.valueOf(enPedidos)).setFontSize(9))
                        .setBackgroundColor(rowBg)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER).setPadding(3));

                // Sistema (rojo si negativo)
                com.itextpdf.layout.element.Paragraph sistemaP = new com.itextpdf.layout.element.Paragraph(String.valueOf(sistema))
                        .setFontSize(9).setBold();
                if (alerta) sistemaP.setFontColor(alertRed);
                table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(sistemaP)
                        .setBackgroundColor(rowBg)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER).setPadding(3));

                // Activo
                table.addCell(mkCell.apply(product.active() ? "Sí" : "No", com.itextpdf.layout.properties.TextAlignment.CENTER));
            }

            document.add(table);

            // ---- Resumen y leyenda ----
            long alertCount = products.stream().filter(p -> {
                StockSummaryDTO s = stockMap.get(p.id());
                return s != null && s.stockEnBD() != null && s.stockEnBD() < 0;
            }).count();

            document.add(new com.itextpdf.layout.element.Paragraph(
                    "\nTotal productos: " + products.size() +
                    (alertCount > 0 ? "     ⚠ Productos con stock negativo: " + alertCount : ""))
                    .setFontSize(10).setBold().setMarginTop(10));

            document.add(new com.itextpdf.layout.element.Paragraph(
                    "Leyenda  |  En Bodega: unidades físicas reales en almacén  " +
                    "|  En Pedidos: comprometidas en pedidos activos (no despachados)  " +
                    "|  Sistema: stock en BD (rojo = negativo)")
                    .setFontSize(8)
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY)
                    .setMarginTop(4));

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF de inventario", e);
            throw new BusinessExeption("Error generando el PDF de inventario: " + e.getMessage());
        }
    }

    // =====================================================================
    // STOCK COMPROMETIDO (Stock Real vs Stock en Pedidos Activos)
    // =====================================================================

    private static final List<OrdenStatus> ESTADOS_FINALES = List.of(
            OrdenStatus.COMPLETADO,
            OrdenStatus.CANCELADO,
            OrdenStatus.ANULADA,
            OrdenStatus.PENDING_PROMOTION_COMPLETION);

    @Override
    public List<StockSummaryDTO> getStockReport() {
        return ordenItemRepository.findStockSummaryWithCommitted(ESTADOS_FINALES);
    }

    @Override
    public List<StockSummaryDTO> getStockAlerts() {
        return getStockReport().stream()
                .filter(StockSummaryDTO::isAlertaCritica)
                .toList();
    }

}
