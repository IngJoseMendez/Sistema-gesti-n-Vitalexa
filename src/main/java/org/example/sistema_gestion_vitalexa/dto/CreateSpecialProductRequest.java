package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateSpecialProductRequest(
                String nombre,
                String descripcion,
                BigDecimal precio,
                Integer stock, // solo para standalone; ignorado si parentProductId != null
                Integer reorderPoint,
                String imageUrl,
                String imageBase64,
                String imageFileName,
                UUID tagId,
                UUID parentProductId, // null = standalone, non-null = hard link
                List<UUID> allowedVendorIds // IDs de los vendedores que pueden ver este producto
) {
}
