package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.sistema_gestion_vitalexa.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreatePromotionRequest(
        @NotBlank(message = "El nombre de la promoción es obligatorio") String nombre,

        String descripcion,

        @NotNull(message = "El tipo de promoción es obligatorio") PromotionType type,

        @NotNull(message = "La cantidad a comprar es obligatoria") Integer buyQuantity,

        BigDecimal packPrice, // Para tipo PACK

        UUID mainProductId,

        java.util.List<GiftItemDTO> giftItems, // Lista de regalos

        Boolean allowStackWithDiscounts, // Default: false

        Boolean requiresAssortmentSelection, // Default: true para PACK

        LocalDateTime validFrom,

        LocalDateTime validUntil) {
}
