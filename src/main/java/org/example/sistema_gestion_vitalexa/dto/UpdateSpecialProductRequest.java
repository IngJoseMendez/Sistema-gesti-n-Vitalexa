package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateSpecialProductRequest(
                String nombre,
                String descripcion,
                BigDecimal precio,
                Integer stock, // solo para standalone
                Integer reorderPoint,
                String imageUrl,
                Boolean active,
                UUID tagId,
                List<UUID> allowedVendorIds // null = no cambiar, lista vacía = quitar todos
// parentProductId NO se puede cambiar una vez creado
) {
}
