package org.example.sistema_gestion_vitalexa.dto;

import org.example.sistema_gestion_vitalexa.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SpecialPromotionResponse(
        UUID id,
        String nombre,
        String descripcion,
        PromotionType type,
        Integer buyQuantity,
        BigDecimal packPrice,
        UUID mainProductId,
        String mainProductName, // To display linked product name
        boolean active,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        UUID parentPromotionId,
        String parentPromotionName,
        boolean isLinked,
        List<UUID> allowedVendorIds,
        List<String> allowedVendorNames) {
    // Helpers for effective values can be added to the entity or service logic,
    // but the response usually carries the raw data + computed fields if needed.
    // For now, we return what is stored.
}
