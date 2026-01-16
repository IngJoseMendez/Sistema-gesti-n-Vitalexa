package org.example.sistema_gestion_vitalexa.controller.owner;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateProductRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateProductRequest;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.service.ProductImageService;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class ProductOwnerController {

    private static final Logger log = LoggerFactory.getLogger(ProductOwnerController.class);

    private final ProductService productService;
    private final ProductImageService imageService;

    // ========== READ OPERATIONS ==========

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> products = productService.findAllAdmin();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/active")
    public ResponseEntity<List<ProductResponse>> getActiveProducts() {
        List<ProductResponse> products = productService.findAllActive();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts(
            @RequestParam(defaultValue = "10") int threshold) {
        List<ProductResponse> products = productService.findLowStock(threshold);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id) {
        ProductResponse product = productService.findById(id);
        return ResponseEntity.ok(product);
    }

    // ========== WRITE OPERATIONS ==========

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @RequestParam String nombre,
            @RequestParam String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam Integer stock,
            @RequestParam(required = false, defaultValue = "10") Integer reorderPoint,  // ← AGREGAR
            @RequestParam(required = false) MultipartFile image) {
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
                    imageUrl
            );

            ProductResponse response = productService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException e) {
            log.error("Error guardando imagen al crear producto", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable UUID id,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false) String precio,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) String reorderPoint,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) Boolean active) {
        try {
            log.debug("Update request id={} nombre={}", id, nombre);

            BigDecimal precioVal = null;
            Integer stockVal = null;
            Integer reorderPointVal = null;

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

            // ← AGREGAR VALIDACIÓN
            if (reorderPoint != null && !reorderPoint.isBlank()) {
                try {
                    reorderPointVal = Integer.valueOf(reorderPoint);
                } catch (NumberFormatException nfe) {
                    log.warn("Reorder point inválido recibido: {}", reorderPoint);
                    return ResponseEntity.badRequest().body("Punto de reorden inválido");
                }
            }

            String imageUrl = null;
            if (image != null && !image.isEmpty()) {
                imageUrl = imageService.saveImage(image);
            }

            // ← ORDEN CORRECTO
            UpdateProductRequest request = new UpdateProductRequest(
                    nombre,
                    descripcion,
                    precioVal,
                    stockVal,
                    reorderPointVal,
                    imageUrl,
                    active,
                    null  // tagId no se actualiza aquí
            );

            ProductResponse response = productService.update(id, request);
            return ResponseEntity.ok(response);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable UUID id,
            @RequestParam(name = "hard", required = false, defaultValue = "false") boolean hard) {
        try {
            log.debug("Delete request for id={} hard={}", id, hard);
            if (hard) {
                productService.hardDelete(id);
            } else {
                productService.softDelete(id);
            }
            return ResponseEntity.noContent().build();
        } catch (BusinessExeption be) {
            log.warn("Business error deleting product {}: {}", id, be.getMessage());
            return ResponseEntity.badRequest().body(be.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error deleting product {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error eliminando producto");
        }
    }
}
