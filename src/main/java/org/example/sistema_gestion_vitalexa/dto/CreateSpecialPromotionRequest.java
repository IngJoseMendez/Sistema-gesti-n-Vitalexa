package org.example.sistema_gestion_vitalexa.dto;

import org.example.sistema_gestion_vitalexa.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateSpecialPromotionRequest(
        String nombre,
        String descripcion,
        PromotionType type,
        Integer buyQuantity,
        BigDecimal packPrice,
        UUID mainProductId,
        Boolean active,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        UUID parentPromotionId,
        List<UUID> allowedVendorIds) {
}
