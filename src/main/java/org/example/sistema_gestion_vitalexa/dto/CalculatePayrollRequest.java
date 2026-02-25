package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Request para calcular/crear la nómina de un vendedor para un mes/año específico.
 */
public record CalculatePayrollRequest(

        @NotNull(message = "El ID del vendedor es obligatorio")
        UUID vendedorId,

        @NotNull(message = "El mes es obligatorio")
        @Min(value = 1) @Max(value = 12)
        Integer month,

        @NotNull(message = "El año es obligatorio")
        @Min(value = 2024)
        Integer year,

        /** Notas adicionales opcionales */
        String notes
) {}

