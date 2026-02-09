package org.example.sistema_gestion_vitalexa.controller.admin;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateProductRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateProductRequest;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.service.ProductImageService;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.example.sistema_gestion_vitalexa.service.ProductAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class ProductAdminController {

    private static final Logger log = LoggerFactory.getLogger(ProductAdminController.class);

    private final ProductService productService;
    private final ProductImageService imageService;
    private final ProductAuditService auditService;

    /**
     * Crear múltiples productos y descargar huella contable
     */
    @PostMapping("/bulk")
    public ResponseEntity<byte[]> createBulk(
            @RequestBody List<CreateProductRequest> requests,
            org.springframework.security.core.Authentication authentication) {

        // 1. Crear productos
        List<ProductResponse> createdProducts = productService.createBulk(requests);

        // 2. Generar PDF
        String username = authentication != null ? authentication.getName() : "Unknown";
        byte[] pdfBytes = auditService.generateProductAudit(createdProducts, username, "CREACIÓN MASIVA");

        // 3. Retornar PDF
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=huella_contable_creacion_" + System.currentTimeMillis() + ".pdf")
                .header("Content-Type", "application/pdf")
                .body(pdfBytes);
    }

    /**
     * Actualizar múltiples productos y descargar huella contable
     */
    @PutMapping("/bulk")
    public ResponseEntity<byte[]> updateBulk(
            @RequestBody List<org.example.sistema_gestion_vitalexa.dto.UpdateProductBulkRequest> requests,
            org.springframework.security.core.Authentication authentication) {

        // 1. Actualizar productos
        List<ProductResponse> updatedProducts = productService.updateBulk(requests);

        // 2. Generar PDF
        String username = authentication != null ? authentication.getName() : "Unknown";
        byte[] pdfBytes = auditService.generateProductAudit(updatedProducts, username, "ACTUALIZACIÓN MASIVA");

        // 3. Retornar PDF
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=huella_contable_actualizacion_" + System.currentTimeMillis() + ".pdf")
                .header("Content-Type", "application/pdf")
                .body(pdfBytes);
    }

    /**
     * Crear nuevo producto con imagen
     */
    @PostMapping
    public ResponseEntity<byte[]> create(
            @RequestParam String nombre,
            @RequestParam String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam Integer stock,
            @RequestParam(required = false, defaultValue = "10") Integer reorderPoint,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) MultipartFile image,
            org.springframework.security.core.Authentication authentication) {
        try {
            String imageUrl = null;
            if (image != null && !image.isEmpty()) {
                imageUrl = imageService.saveImage(image);
            }

            CreateProductRequest request = new CreateProductRequest(
                    nombre,
                    descripcion,
                    precio,
                    stock,
                    reorderPoint,
                    imageUrl,
                    tagId);

            ProductResponse createdProduct = productService.create(request);

            // Generar PDF para auditoría
            String username = authentication != null ? authentication.getName() : "Unknown";
            byte[] pdfBytes = auditService.generateProductAudit(List.of(createdProduct), username,
                    "CREACIÓN INDIVIDUAL");

            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=huella_contable_creacion_" + System.currentTimeMillis() + ".pdf")
                    .header("Content-Type", "application/pdf")
                    .body(pdfBytes);
        } catch (IOException e) {
            log.error("Error guardando imagen al crear producto", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Actualizar producto existente
     */
    @PutMapping("/{id}")
    public ResponseEntity<byte[]> update(
            @PathVariable UUID id,
            @RequestBody UpdateProductRequest request,
            org.springframework.security.core.Authentication authentication) {
        try {
            log.debug("Update request id={} request={}", id, request);

            ProductResponse updatedProduct = productService.update(id, request);

            // Generar PDF para auditoría
            String username = authentication != null ? authentication.getName() : "Unknown";
            byte[] pdfBytes = auditService.generateProductAudit(List.of(updatedProduct), username,
                    "ACTUALIZACIÓN INDIVIDUAL");

            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=huella_contable_actualizacion_" + System.currentTimeMillis() + ".pdf")
                    .header("Content-Type", "application/pdf")
                    .body(pdfBytes);
        } catch (BusinessExeption be) {
            log.warn("Business error updating product {}: {}", id, be.getMessage());
            return ResponseEntity.badRequest().body(be.getMessage().getBytes()); // Convert message to bytes
        } catch (Exception e) {
            log.error("Unexpected error updating product {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error actualizando producto".getBytes()); // Convert message to bytes
        }
    }

    /**
     * Endpoint alternativo para actualizar (POST multipart)
     */
    @PostMapping("/{id}/update")
    public ResponseEntity<?> updateViaPost(
            @PathVariable UUID id,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false) String precio,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) String reorderPoint,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) Boolean active,
            org.springframework.security.core.Authentication authentication) {
        try {
            log.debug("Update via POST request id={} nombre={} precio={} stock={} active={}",
                    id, nombre, precio, stock, active);

            BigDecimal precioVal = null;
            Integer stockVal = null;
            Integer reorderPointVal = null;
            String imageUrl = null;

            if (precio != null && !precio.isBlank()) {
                try {
                    String normalized = precio.replace(',', '.').trim();
                    precioVal = new BigDecimal(normalized);
                } catch (NumberFormatException nfe) {
                    log.warn("Precio inválido recibido: {}", precio);
                    return ResponseEntity.badRequest().body("Precio inválido");
                }
            }

            if (stock != null && !stock.isBlank()) {
                try {
                    stockVal = Integer.valueOf(stock);
                } catch (NumberFormatException nfe) {
                    log.warn("Stock inválido recibido: {}", stock);
                    return ResponseEntity.badRequest().body("Stock inválido");
                }
            }

            if (reorderPoint != null && !reorderPoint.isBlank()) {
                try {
                    reorderPointVal = Integer.valueOf(reorderPoint);
                } catch (NumberFormatException nfe) {
                    log.warn("Reorder point inválido recibido: {}", reorderPoint);
                    return ResponseEntity.badRequest().body("Punto de reorden inválido");
                }
            }

            if (image != null && !image.isEmpty()) {
                imageUrl = imageService.saveImage(image);
            }

            UpdateProductRequest request = new UpdateProductRequest(
                    nombre,
                    descripcion,
                    precioVal,
                    stockVal,
                    reorderPointVal,
                    imageUrl,
                    active,
                    tagId);

            ProductResponse updatedProduct = productService.update(id, request);

            // Generar PDF para auditoría
            String username = authentication != null ? authentication.getName() : "Unknown";
            byte[] pdfBytes = auditService.generateProductAudit(List.of(updatedProduct), username,
                    "ACTUALIZACIÓN INDIVIDUAL (Multipart)");

            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=huella_contable_actualizacion_multipart_" + System.currentTimeMillis()
                                    + ".pdf")
                    .header("Content-Type", "application/pdf")
                    .body(pdfBytes);
        } catch (BusinessExeption be) {
            log.warn("Business error updating product {}: {}", id, be.getMessage());
            return ResponseEntity.badRequest().body(be.getMessage());
        } catch (IOException ioe) {
            log.error("IO error when saving image for product {}", id, ioe);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error guardando imagen");
        } catch (Exception e) {
            log.error("Unexpected error updating product {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error actualizando producto");
        }
    }

    /**
     * Eliminar (soft delete) o hard delete si se pasa ?hard=true
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<byte[]> delete(
            @PathVariable UUID id,
            @RequestParam(name = "hard", required = false, defaultValue = "false") boolean hard,
            org.springframework.security.core.Authentication authentication) {
        try {
            // Obtenemos el producto antes de eliminarlo para la auditoría
            ProductResponse productToDelete = productService.findById(id);

            if (hard) {
                productService.hardDelete(id);
            } else {
                productService.softDelete(id);
            }

            // Generar PDF para auditoría
            String username = authentication != null ? authentication.getName() : "Unknown";
            String opType = hard ? "ELIMINACIÓN FÍSICA (HARD DELETE)" : "ELIMINACIÓN LÓGICA (SOFT DELETE)";
            byte[] pdfBytes = auditService.generateProductAudit(List.of(productToDelete), username, opType);

            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=huella_contable_eliminacion_" + System.currentTimeMillis() + ".pdf")
                    .header("Content-Type", "application/pdf")
                    .body(pdfBytes);
        } catch (BusinessExeption be) {
            log.warn("Business error deleting product {}: {}", id, be.getMessage());
            return ResponseEntity.badRequest().body(be.getMessage().getBytes());
        } catch (Exception e) {
            log.error("Unexpected error deleting product {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error eliminando producto".getBytes());
        }
    }

    /**
     * Obtener todos los productos (incluyendo inactivos)
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        List<ProductResponse> productos = productService.findAllAdmin();
        return ResponseEntity.ok(productos);
    }

    /**
     * Obtener solo productos activos
     */
    @GetMapping("/active")
    public ResponseEntity<List<ProductResponse>> findAllActive() {
        List<ProductResponse> productos = productService.findAllActive();
        return ResponseEntity.ok(productos);
    }

    /**
     * Obtener producto por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable UUID id) {
        ProductResponse producto = productService.findById(id);
        return ResponseEntity.ok(producto);
    }

    /**
     * Cambiar estado de un producto (activo/inactivo)
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<?> changeStatus(
            @PathVariable UUID id,
            @RequestParam boolean activo) {
        try {
            log.debug("Change status request id={} activo={}", id, activo);
            productService.changeStatus(id, activo);
            return ResponseEntity.noContent().build();
        } catch (BusinessExeption be) {
            log.warn("Business error changing status for {}: {}", id, be.getMessage());
            return ResponseEntity.badRequest().body(be.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error changing status for {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error cambiando estado");
        }
    }

    /**
     * GET /api/admin/products/tag/{tagId} - Filtrar productos por etiqueta
     */
    @GetMapping("/tag/{tagId}")
    public ResponseEntity<Page<ProductResponse>> findByTag(
            @PathVariable UUID tagId,
            Pageable pageable) {
        Page<ProductResponse> productos = productService.findByTag(tagId, pageable);
        return ResponseEntity.ok(productos);
    }

    /**
     * GET /api/admin/products/tag/{tagId}/search - Buscar productos por etiqueta y
     * término
     */
    @GetMapping("/tag/{tagId}/search")
    public ResponseEntity<Page<ProductResponse>> searchByTag(
            @PathVariable UUID tagId,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        Page<ProductResponse> productos = productService.searchByTag(q, tagId, pageable);
        return ResponseEntity.ok(productos);
    }

    /**
     * GET /api/admin/products/search - Buscar productos globalmente
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> search(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        Page<ProductResponse> productos = productService.searchActive(q, pageable);
        return ResponseEntity.ok(productos);
    }
}
