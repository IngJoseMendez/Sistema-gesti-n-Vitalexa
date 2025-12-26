package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSaleGoalRequest(
        @NotNull(message = "El ID del vendedor es obligatorio")
        UUID vendedorId,

        @NotNull(message = "La meta es obligatoria")
        @DecimalMin(value = "0.01", message = "La meta debe ser mayor a 0")
        BigDecimal targetAmount,

        @NotNull(message = "El mes es obligatorio")
        @Min(value = 1, message = "El mes debe estar entre 1 y 12")
        @Max(value = 12, message = "El mes debe estar entre 1 y 12")
        Integer month,

        @NotNull(message = "El año es obligatorio")
        @Min(value = 2024, message = "El año debe ser válido")
        Integer year
) {}
