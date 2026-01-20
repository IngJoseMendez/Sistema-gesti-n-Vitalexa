package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response con información de un pago
 */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        LocalDateTime paymentDate,
        Boolean withinDeadline,
        BigDecimal discountApplied,
        String registeredByUsername,
        LocalDateTime createdAt,
        String notes) {
}
