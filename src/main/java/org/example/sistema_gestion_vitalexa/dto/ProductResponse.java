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
        Integer reorderPoint,
        UUID tagId,
        String tagName,
        Integer linkedSpecialCount,
        Boolean isSpecialProduct,
        UUID specialProductId) {

    /**
     * Constructor sin tag (backward compatibility)
     */
    public ProductResponse(UUID id, String nombre, String descripcion, BigDecimal precio, Integer stock,
            String imageUrl, Boolean active) {
        this(id, nombre, descripcion, precio, stock, imageUrl, active, null, null, null, 0, false, null);
    }

    /**
     * Constructor para productos regulares (sin info de special product)
     */
    public ProductResponse(UUID id, String nombre, String descripcion, BigDecimal precio, Integer stock,
            String imageUrl, Boolean active, Integer reorderPoint, UUID tagId, String tagName,
            Integer linkedSpecialCount) {
        this(id, nombre, descripcion, precio, stock, imageUrl, active, reorderPoint, tagId, tagName,
                linkedSpecialCount, false, null);
    }
}
