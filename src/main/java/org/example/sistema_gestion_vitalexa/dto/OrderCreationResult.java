package org.example.sistema_gestion_vitalexa.dto;

import java.util.List;

/**
 * DTO para responder múltiples órdenes cuando se crea una orden que se divide
 */
public record OrderCreationResult(
        List<OrderResponse> orders,
        boolean wasSplit,
        String message
) {
    /**
     * Constructor para una sola orden (sin split)
     */
    public static OrderCreationResult single(OrderResponse order) {
        return new OrderCreationResult(List.of(order), false, "Orden creada exitosamente");
    }

    /**
     * Constructor para dos órdenes (con split)
     */
    public static OrderCreationResult split(OrderResponse normalOrder, OrderResponse srOrder) {
        return new OrderCreationResult(
                List.of(normalOrder, srOrder),
                true,
                "Orden dividida: una con productos normales y otra con productos S/R"
        );
    }
}

