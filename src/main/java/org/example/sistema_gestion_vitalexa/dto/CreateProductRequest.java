package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.UUID;


public record CreateProductRequest(
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        Integer reorderPoint,  // ← Posición 5
        String imageUrl,       // ← Posición 6
        UUID tagId             // ← Nuevo: Posición 7 (optional)
) {
    /**
     * Constructor sin tagId (backward compatibility)
     */
    public CreateProductRequest(String nombre, String descripcion, BigDecimal precio, Integer stock, Integer reorderPoint, String imageUrl) {
        this(nombre, descripcion, precio, stock, reorderPoint, imageUrl, null);
    }
}

