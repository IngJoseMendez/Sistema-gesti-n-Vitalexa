package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.NotEmpty;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderRequestDto(
                @NotNull UUID clientId,

                @NotEmpty List<OrderItemRequestDTO> items,

                String notas,

                List<UUID> promotionIds // IDs de promociones a aplicar
) {
}
