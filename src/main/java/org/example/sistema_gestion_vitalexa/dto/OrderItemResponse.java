package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrderItemResponse(
                UUID productId,
                String productName,
                Integer cantidad,
                BigDecimal precioUnitario,
                BigDecimal subtotal,

                // Campos para productos sin stock
                Boolean outOfStock,
                LocalDate estimatedArrivalDate,
                String estimatedArrivalNote,

                // Campos para promociones
                UUID promotionId,
                String promotionName,
                Boolean isPromotionItem,
                Boolean isFreeItem) {
}
