package org.example.sistema_gestion_vitalexa.dto;

import org.antlr.v4.runtime.misc.NotNull;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record OrderItemRequestDTO(

                @NotNull UUID productId,
                @Min(1) Integer cantidad,
                Boolean allowOutOfStock // Permite agregar sin stock (solo vendedor)
) {
}
