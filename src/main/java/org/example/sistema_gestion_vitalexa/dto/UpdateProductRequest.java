package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductRequest(
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        Integer reorderPoint,  // ← Posición 5
        String imageUrl,       // ← Posición 6
        Boolean active,        // ← Posición 7
        UUID tagId             // ← Nuevo: Posición 8 (optional)
) {
}


