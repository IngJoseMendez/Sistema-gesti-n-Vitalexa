package org.example.sistema_gestion_vitalexa.controller.admin;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateSpecialPromotionRequest;
import org.example.sistema_gestion_vitalexa.dto.SpecialPromotionResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSpecialPromotionRequest;
import org.example.sistema_gestion_vitalexa.service.SpecialPromotionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/special-promotions")
@RequiredArgsConstructor
public class SpecialPromotionAdminController {

    private static final Logger log = LoggerFactory.getLogger(SpecialPromotionAdminController.class);

    private final SpecialPromotionService specialPromotionService;

    // ========================================================
    // CREATE
    // ========================================================

    @PostMapping
    public ResponseEntity<SpecialPromotionResponse> create(@RequestBody CreateSpecialPromotionRequest request) {
        log.info("Creando promoción especial: '{}' (padre={})", request.nombre(), request.parentPromotionId());
        SpecialPromotionResponse response = specialPromotionService.create(request);
        return ResponseEntity.ok(response);
    }

    // ========================================================
    // UPDATE
    // ========================================================

    @PutMapping("/{id}")
    public ResponseEntity<SpecialPromotionResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateSpecialPromotionRequest request) {
        log.info("Actualizando promoción especial: {}", id);
        SpecialPromotionResponse response = specialPromotionService.update(id, request);
        return ResponseEntity.ok(response);
    }

    // ========================================================
    // FIND
    // ========================================================

    @GetMapping
    public ResponseEntity<Page<SpecialPromotionResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(specialPromotionService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpecialPromotionResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(specialPromotionService.findById(id));
    }

    @GetMapping("/by-parent/{parentId}")
    public ResponseEntity<List<SpecialPromotionResponse>> findByParent(@PathVariable UUID parentId) {
        return ResponseEntity.ok(specialPromotionService.findByParent(parentId));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SpecialPromotionResponse>> search(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(specialPromotionService.search(q, pageable));
    }

    // ========================================================
    // DELETE / STATUS
    // ========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("Soft-delete promoción especial: {}", id);
        specialPromotionService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        log.info("Cambiar estado promoción especial {} -> {}", id, active);
        specialPromotionService.changeStatus(id, active);
        return ResponseEntity.noContent().build();
    }
}
