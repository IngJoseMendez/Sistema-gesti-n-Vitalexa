package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request para calcular/crear la nómina de un vendedor para un mes/año
 * específico.
 */
public record CalculatePayrollRequest(

                @NotNull(message = "El ID del vendedor es obligatorio") UUID vendedorId,

                @NotNull(message = "El mes es obligatorio") @Min(value = 1) @Max(value = 12) Integer month,

                @NotNull(message = "El año es obligatorio") @Min(value = 2024) Integer year,

                /** Notas adicionales opcionales */
                String notes,

                /**
                 * Umbral personalizado para la comisión general (opcional).
                 * Si se provee, las ventas de la empresa deben superar este valor
                 * para que la comisión general aplique.
                 * Si es null, se usa la suma de todas las metas de los vendedores del mes.
                 */
                BigDecimal generalCommissionThreshold) {
}
