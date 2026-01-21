package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para agregar productos surtidos a una promoción en una orden
 */
public record AddAssortmentItemRequest(
        @NotNull(message = "El ID del producto es obligatorio") UUID productId,

        @NotNull(message = "La cantidad es obligatoria") Integer cantidad) {
}
