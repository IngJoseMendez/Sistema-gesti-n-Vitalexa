package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request para registrar un pago/abono (solo Owner)
 */
public record CreatePaymentRequest(
        @NotNull(message = "El ID de la orden es obligatorio") java.util.UUID orderId,

        @NotNull(message = "El monto es obligatorio") @Positive(message = "El monto debe ser positivo") BigDecimal amount,

        Boolean withinDeadline,

        BigDecimal discountApplied,

        String notes) {
}
