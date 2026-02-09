package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductBulkRequest(
                UUID id, // Required for bulk update identification
                String nombre,
                String descripcion,
                BigDecimal precio,
                Integer stock,
                Integer reorderPoint,
                String imageUrl,
                String imageBase64,
                String imageFileName,
                Boolean active,
                UUID tagId) {
}
