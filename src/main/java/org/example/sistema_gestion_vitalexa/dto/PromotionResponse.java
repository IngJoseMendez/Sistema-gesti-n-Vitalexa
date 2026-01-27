package org.example.sistema_gestion_vitalexa.dto;

import org.example.sistema_gestion_vitalexa.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PromotionResponse(
                UUID id,
                String nombre,
                String descripcion,
                PromotionType type,
                Integer buyQuantity,
                BigDecimal packPrice,
                ProductResponse mainProduct,
                java.util.List<GiftItemResponse> giftItems,
                Boolean allowStackWithDiscounts,
                Boolean requiresAssortmentSelection,
                Boolean active,
                LocalDateTime validFrom,
                LocalDateTime validUntil,
                LocalDateTime createdAt,
                Boolean isValid // Calculado: si está dentro del período de validez
) {
}
