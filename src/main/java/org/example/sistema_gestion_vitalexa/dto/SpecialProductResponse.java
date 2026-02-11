package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SpecialProductResponse(
                UUID id,
                String nombre,
                String descripcion,
                BigDecimal precio,
                Integer stock, // effectiveStock (del padre o propio)
                String imageUrl,
                Boolean active,
                Integer reorderPoint,
                UUID tagId,
                String tagName,
                UUID parentProductId, // null si standalone
                String parentProductName,
                boolean linked, // true si tiene padre
                List<UUID> allowedVendorIds,
                List<String> allowedVendorNames) {
}
