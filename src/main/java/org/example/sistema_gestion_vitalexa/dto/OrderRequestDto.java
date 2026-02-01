package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.NotEmpty;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderRequestDto(
        @NotNull UUID clientId,
        List<OrderItemRequestDTO> items, // Productos normales (opcional si hay promotionIds)
        List<BonifiedItemRequestDTO> bonifiedItems, // Productos bonificados (sección separada)
        String notas,
        List<UUID> promotionIds,
        Boolean includeFreight,
        UUID sellerId, // ID del vendedor (Solo para Admin/Owner)
        Boolean isFreightBonified,
        String freightCustomText,
        Integer freightQuantity) {
}
