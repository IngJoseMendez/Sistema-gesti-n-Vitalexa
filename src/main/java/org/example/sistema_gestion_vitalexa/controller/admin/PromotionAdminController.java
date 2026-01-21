package org.example.sistema_gestion_vitalexa.controller.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.CreatePromotionRequest;
import org.example.sistema_gestion_vitalexa.dto.PromotionResponse;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.service.PromotionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
@Slf4j
public class PromotionAdminController {

    private final PromotionService promotionService;

    /**
     * POST /api/admin/promotions - Crear promoción
     */
    @PostMapping
    public ResponseEntity<PromotionResponse> create(@Valid @RequestBody CreatePromotionRequest request) {
        try {
            log.info("Admin creando promoción: {}", request.nombre());
            PromotionResponse response = promotionService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (BusinessExeption e) {
            log.warn("Error creando promoción: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * PUT /api/admin/promotions/{id} - Actualizar promoción
     */
    @PutMapping("/{id}")
    public ResponseEntity<PromotionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePromotionRequest request) {
        try {
            log.info("Admin actualizando promoción ID: {}", id);
            PromotionResponse response = promotionService.update(id, request);
            return ResponseEntity.ok(response);
        } catch (BusinessExeption e) {
            log.warn("Error actualizando promoción {}: {}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * DELETE /api/admin/promotions/{id} - Eliminar promoción
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            log.info("Admin eliminando promoción ID: {}", id);
            promotionService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (BusinessExeption e) {
            log.warn("Error eliminando promoción {}: {}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * PATCH /api/admin/promotions/{id}/status - Cambiar estado
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        try {
            log.info("Admin cambiando estado de promoción {} a {}", id, active);
            promotionService.changeStatus(id, active);
            return ResponseEntity.noContent().build();
        } catch (BusinessExeption e) {
            log.warn("Error cambiando estado de promoción {}: {}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * GET /api/admin/promotions - Listar todas las promociones
     */
    @GetMapping
    public ResponseEntity<List<PromotionResponse>> findAll() {
        log.info("Admin listando todas las promociones");
        List<PromotionResponse> promotions = promotionService.findAll();
        return ResponseEntity.ok(promotions);
    }

    /**
     * GET /api/admin/promotions/{id} - Obtener promoción por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<PromotionResponse> findById(@PathVariable UUID id) {
        try {
            log.info("Admin obteniendo promoción ID: {}", id);
            PromotionResponse response = promotionService.findById(id);
            return ResponseEntity.ok(response);
        } catch (BusinessExeption e) {
            log.warn("Promoción no encontrada: {}", id);
            throw e;
        }
    }
}
