package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.CreateProductTagRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductTagResponse;
import org.example.sistema_gestion_vitalexa.entity.ProductTag;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.ProductTagRepository;
import org.example.sistema_gestion_vitalexa.service.ProductTagService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProductTagServiceImpl implements ProductTagService {

    private final ProductTagRepository tagRepository;

    private static final String SR_TAG_NAME = "S/R";

    /**
     * Obtener todas las etiquetas
     */
    @Override
    @Transactional
    public List<ProductTagResponse> findAll() {
        return tagRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Obtener una etiqueta por ID
     */
    @Override
    @Transactional
    public ProductTagResponse findById(UUID id) {
        ProductTag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Etiqueta no encontrada"));
        return toResponse(tag);
    }

    /**
     * Obtener la etiqueta del sistema "S/R"
     */
    @Override
    @Transactional
    public ProductTagResponse getSRTag() {
        return toResponse(getSRTagEntity());
    }

    /**
     * Obtener etiqueta del sistema "S/R" como entidad
     */
    @Override
    @Transactional
    public ProductTag getSRTagEntity() {
        return tagRepository.findSRTag()
                .orElseThrow(() -> new BusinessExeption("Etiqueta del sistema 'S/R' no encontrada"));
    }

    /**
     * Crear nueva etiqueta (solo ADMIN)
     */
    @Override
    public ProductTagResponse createTag(CreateProductTagRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessExeption("El nombre de la etiqueta no puede estar vacío");
        }

        String nameNormalized = request.name().trim();

        // Prohibir crear una etiqueta con el mismo nombre que la del sistema
        if (nameNormalized.equalsIgnoreCase(SR_TAG_NAME)) {
            throw new BusinessExeption("No se puede crear una etiqueta con el nombre reservado 'S/R'");
        }

        // Validar que no exista ya
        if (tagRepository.findByNameIgnoreCase(nameNormalized).isPresent()) {
            throw new BusinessExeption("Ya existe una etiqueta con el nombre: " + nameNormalized);
        }

        ProductTag tag = ProductTag.builder()
                .name(nameNormalized)
                .isSystem(false)
                .build();

        ProductTag saved = tagRepository.save(tag);
        log.info("Etiqueta creada: {} ({})", saved.getName(), saved.getId());
        return toResponse(saved);
    }

    /**
     * Actualizar etiqueta (solo ADMIN, no se puede cambiar la etiqueta "S/R")
     */
    @Override
    public ProductTagResponse updateTag(UUID id, CreateProductTagRequest request) {
        ProductTag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Etiqueta no encontrada"));

        // Prohibir editar la etiqueta del sistema
        if (tag.isSRTag()) {
            throw new BusinessExeption("No se puede editar la etiqueta del sistema 'S/R'");
        }

        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessExeption("El nombre de la etiqueta no puede estar vacío");
        }

        String nameNormalized = request.name().trim();

        // Validar que el nuevo nombre no coincida con otra etiqueta (excepto la misma)
        tagRepository.findByNameIgnoreCase(nameNormalized).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessExeption("Ya existe otra etiqueta con el nombre: " + nameNormalized);
            }
        });

        tag.setName(nameNormalized);
        ProductTag updated = tagRepository.save(tag);
        log.info("Etiqueta actualizada: {} ({})", updated.getName(), updated.getId());
        return toResponse(updated);
    }

    /**
     * Eliminar etiqueta (solo ADMIN, no se puede eliminar "S/R")
     */
    @Override
    public void deleteTag(UUID id) {
        ProductTag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Etiqueta no encontrada"));

        // Prohibir eliminar la etiqueta del sistema
        if (tag.isSRTag()) {
            throw new BusinessExeption("No se puede eliminar la etiqueta del sistema 'S/R'");
        }

        if (tag.getIsSystem()) {
            throw new BusinessExeption("No se pueden eliminar etiquetas del sistema");
        }

        tagRepository.delete(tag);
        log.info("Etiqueta eliminada: {} ({})", tag.getName(), tag.getId());
    }

    /**
     * Obtener etiqueta por nombre (búsqueda case-insensitive)
     */
    @Override
    @Transactional
    public ProductTag findByNameIgnoreCase(String name) {
        return tagRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new BusinessExeption("Etiqueta no encontrada: " + name));
    }

    /**
     * Obtener etiqueta como entidad por UUID
     */
    @Override
    @Transactional
    public ProductTag findEntityById(UUID id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Etiqueta no encontrada"));
    }

    // ========================
    // PRIVATE HELPERS
    // ========================

    private ProductTagResponse toResponse(ProductTag tag) {
        return new ProductTagResponse(tag.getId(), tag.getName(), tag.getIsSystem());
    }
}

