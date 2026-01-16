package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        String imageUrl,
        Boolean active,
        UUID tagId,
        String tagName
) {
    /**
     * Constructor sin tag (backward compatibility)
     */
    public ProductResponse(UUID id, String nombre, String descripcion, BigDecimal precio, Integer stock, String imageUrl, Boolean active) {
        this(id, nombre, descripcion, precio, stock, imageUrl, active, null, null);
    }
}
