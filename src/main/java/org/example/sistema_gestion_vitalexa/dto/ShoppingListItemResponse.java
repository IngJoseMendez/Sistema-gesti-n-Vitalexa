package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

public record ShoppingListItemResponse(
        UUID itemId,
        UUID productId,
        String productName,
        Integer defaultQty
) {}
