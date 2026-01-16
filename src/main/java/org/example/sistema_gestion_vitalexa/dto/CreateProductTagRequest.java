package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

/**
 * DTO para crear/actualizar una etiqueta de producto
 */
public record CreateProductTagRequest(
        String name
) {
}

