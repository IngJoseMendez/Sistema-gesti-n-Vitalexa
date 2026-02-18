package org.example.sistema_gestion_vitalexa.dto;

import org.example.sistema_gestion_vitalexa.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response con información de un pago
 */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        LocalDateTime paymentDate, // Timestamp de registro
        LocalDate actualPaymentDate, // Fecha real del pago
        PaymentMethod paymentMethod,
        Boolean withinDeadline,
        BigDecimal discountApplied,
        String registeredByUsername,
        LocalDateTime createdAt,
        String notes,
        Boolean isCancelled,
        LocalDateTime cancelledAt,
        String cancelledByUsername,
        String cancellationReason) {
}
