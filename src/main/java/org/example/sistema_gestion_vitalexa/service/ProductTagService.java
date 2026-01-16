package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.CreateProductTagRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductTagResponse;
import org.example.sistema_gestion_vitalexa.entity.ProductTag;

import java.util.List;
import java.util.UUID;

public interface ProductTagService {

    /**
     * Obtener todas las etiquetas
     */
    List<ProductTagResponse> findAll();

    /**
     * Obtener una etiqueta por ID
     */
    ProductTagResponse findById(UUID id);

    /**
     * Obtener la etiqueta del sistema "S/R"
     */
    ProductTagResponse getSRTag();

    /**
     * Obtener etiqueta del sistema "S/R" como entidad
     */
    ProductTag getSRTagEntity();

    /**
     * Crear nueva etiqueta (solo ADMIN)
     */
    ProductTagResponse createTag(CreateProductTagRequest request);

    /**
     * Actualizar etiqueta (solo ADMIN, no se puede cambiar la etiqueta "S/R")
     */
    ProductTagResponse updateTag(UUID id, CreateProductTagRequest request);

    /**
     * Eliminar etiqueta (solo ADMIN, no se puede eliminar "S/R")
     */
    void deleteTag(UUID id);

    /**
     * Obtener etiqueta por nombre (búsqueda case-insensitive)
     */
    ProductTag findByNameIgnoreCase(String name);

    /**
     * Obtener etiqueta como entidad por UUID
     */
    ProductTag findEntityById(UUID id);
}

