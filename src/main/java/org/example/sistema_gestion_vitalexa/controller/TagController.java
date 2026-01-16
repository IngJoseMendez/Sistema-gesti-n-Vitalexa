package org.example.sistema_gestion_vitalexa.controller;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateProductTagRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductTagResponse;
import org.example.sistema_gestion_vitalexa.service.ProductTagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final ProductTagService tagService;

    /**
     * GET /api/tags - Obtener todas las etiquetas (público)
     */
    @GetMapping
    public ResponseEntity<List<ProductTagResponse>> getAllTags() {
        return ResponseEntity.ok(tagService.findAll());
    }

    /**
     * GET /api/tags/{id} - Obtener una etiqueta por ID (público)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductTagResponse> getTagById(@PathVariable UUID id) {
        return ResponseEntity.ok(tagService.findById(id));
    }

    /**
     * GET /api/tags/system/sr - Obtener la etiqueta del sistema "S/R" (público)
     */
    @GetMapping("/system/sr")
    public ResponseEntity<ProductTagResponse> getSRTag() {
        return ResponseEntity.ok(tagService.getSRTag());
    }

    /**
     * POST /api/tags - Crear nueva etiqueta (solo ADMIN)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductTagResponse> createTag(@RequestBody CreateProductTagRequest request) {
        ProductTagResponse response = tagService.createTag(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/tags/{id} - Actualizar etiqueta (solo ADMIN, no se puede editar "S/R")
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductTagResponse> updateTag(
            @PathVariable UUID id,
            @RequestBody CreateProductTagRequest request
    ) {
        ProductTagResponse response = tagService.updateTag(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/tags/{id} - Eliminar etiqueta (solo ADMIN, no se puede eliminar "S/R")
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTag(@PathVariable UUID id) {
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }
}
