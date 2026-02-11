package org.example.sistema_gestion_vitalexa.controller.admin;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateSpecialProductRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.dto.SpecialProductResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSpecialProductRequest;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.example.sistema_gestion_vitalexa.service.SpecialProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/special-products")
@RequiredArgsConstructor
public class SpecialProductAdminController {

    private static final Logger log = LoggerFactory.getLogger(SpecialProductAdminController.class);

    private final SpecialProductService specialProductService;
    private final ProductService productService;

    // ========================================================
    // CREATE
    // ========================================================

    @PostMapping
    public ResponseEntity<SpecialProductResponse> create(@RequestBody CreateSpecialProductRequest request) {
        log.info("Creando producto especial: '{}' (padre={})", request.nombre(), request.parentProductId());
        SpecialProductResponse response = specialProductService.create(request);
        return ResponseEntity.ok(response);
    }

    // ========================================================
    // UPDATE
    // ========================================================

    @PutMapping("/{id}")
    public ResponseEntity<SpecialProductResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateSpecialProductRequest request) {
        log.info("Actualizando producto especial: {}", id);
        SpecialProductResponse response = specialProductService.update(id, request);
        return ResponseEntity.ok(response);
    }

    // ========================================================
    // FIND
    // ========================================================

    @GetMapping
    public ResponseEntity<Page<SpecialProductResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(specialProductService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpecialProductResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(specialProductService.findById(id));
    }

    @GetMapping("/by-parent/{parentId}")
    public ResponseEntity<List<SpecialProductResponse>> findByParent(@PathVariable UUID parentId) {
        return ResponseEntity.ok(specialProductService.findByParent(parentId));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SpecialProductResponse>> search(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(specialProductService.search(q, pageable));
    }

    /**
     * Endpoint de conveniencia: devuelve los datos del producto padre
     * para que el frontend pre-llene el formulario de creación.
     */
    @GetMapping("/parent/{parentId}/data")
    public ResponseEntity<ProductResponse> getParentData(@PathVariable UUID parentId) {
        return ResponseEntity.ok(productService.findById(parentId));
    }

    // ========================================================
    // DELETE / STATUS
    // ========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("Soft-delete producto especial: {}", id);
        specialProductService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable UUID id,
            @RequestParam boolean activo) {
        log.info("Cambiar estado producto especial {} -> {}", id, activo);
        specialProductService.changeStatus(id, activo);
        return ResponseEntity.noContent().build();
    }
}
