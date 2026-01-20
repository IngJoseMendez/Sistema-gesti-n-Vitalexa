package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request para aplicar un descuento a una orden (Admin)
 */
public record ApplyDiscountRequest(
        @NotNull(message = "El ID de la orden es obligatorio") UUID orderId,

        @NotNull(message = "El porcentaje es obligatorio") @Positive(message = "El porcentaje debe ser positivo") @Max(value = 100, message = "El porcentaje máximo es 100") BigDecimal percentage,

        String reason) {
}
