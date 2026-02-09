package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        Integer reorderPoint, // ← Posición 5
        String imageUrl,
        String imageBase64, // ← Nuevo: Para carga masiva
        String imageFileName, // ← Nuevo: Para extensión
        UUID tagId // ← Nuevo: Posición 7 (optional)
) {
    /**
     * Constructor de compatibilidad (7 args)
     */
    public CreateProductRequest(String nombre, String descripcion, BigDecimal precio, Integer stock,
            Integer reorderPoint, String imageUrl, UUID tagId) {
        this(nombre, descripcion, precio, stock, reorderPoint, imageUrl, null, null, tagId);
    }

    /**
     * Constructor de compatibilidad (6 args) - legacy
     */
    public CreateProductRequest(String nombre, String descripcion, BigDecimal precio, Integer stock,
            Integer reorderPoint, String imageUrl) {
        this(nombre, descripcion, precio, stock, reorderPoint, imageUrl, null, null, null);
    }
}
