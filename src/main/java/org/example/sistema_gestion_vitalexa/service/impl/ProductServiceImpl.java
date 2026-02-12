package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.SpecialProduct;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.ProductMapper;
import org.example.sistema_gestion_vitalexa.repository.ProductRepository;
import org.example.sistema_gestion_vitalexa.service.NotificationService;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.example.sistema_gestion_vitalexa.service.ProductTagService;
import org.example.sistema_gestion_vitalexa.service.ProductImageService;
import org.example.sistema_gestion_vitalexa.service.InventoryMovementService;
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

}
