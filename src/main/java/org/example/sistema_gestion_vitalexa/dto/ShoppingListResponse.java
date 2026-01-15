package org.example.sistema_gestion_vitalexa.dto;

import java.util.List;
import java.util.UUID;

public record ShoppingListResponse(
        UUID id,
        String name,
        List<ShoppingListItemResponse> items
) {}
