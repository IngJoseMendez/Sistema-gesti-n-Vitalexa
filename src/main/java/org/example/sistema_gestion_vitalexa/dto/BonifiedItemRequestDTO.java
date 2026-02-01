package org.example.sistema_gestion_vitalexa.dto;

import org.antlr.v4.runtime.misc.NotNull;
import jakarta.validation.constraints.Min;
import java.util.UUID;

/**
 * DTO para agregar/actualizar productos bonificados (regalos) en una orden.
 * Los bonificados se manejan como una sección separada del rest de los productos.
 */
public record BonifiedItemRequestDTO(
        @NotNull UUID productId,
        @Min(1) Integer cantidad
) {
}

