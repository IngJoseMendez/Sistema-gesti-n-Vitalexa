package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

/**
 * DTO de respuesta para una etiqueta de producto
 */
public record ProductTagResponse(
        UUID id,
        String name,
        Boolean isSystem
) {
}

