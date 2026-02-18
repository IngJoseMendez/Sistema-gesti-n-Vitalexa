package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.sistema_gestion_vitalexa.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request para registrar un pago/abono (solo Owner)
 */
public record CreatePaymentRequest(
        @NotNull(message = "El ID de la orden es obligatorio") java.util.UUID orderId,

        @NotNull(message = "El monto es obligatorio") @Positive(message = "El monto debe ser positivo") BigDecimal amount,

        @NotNull(message = "El método de pago es obligatorio") PaymentMethod paymentMethod,

        LocalDate actualPaymentDate, // Fecha real del pago (opcional, si no se envía usa hoy)

        Boolean withinDeadline,

        BigDecimal discountApplied,

        String notes) {
}
