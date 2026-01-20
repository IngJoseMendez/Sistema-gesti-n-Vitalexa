package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response con información de un descuento
 */
public record DiscountResponse(
        UUID id,
        UUID orderId,
        BigDecimal percentage,
        String type,
        String status,
        String appliedByUsername,
        String revokedByUsername,
        LocalDateTime createdAt,
        LocalDateTime revokedAt,
        String reason) {
}
