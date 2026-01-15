package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

public record ShoppingListItemRequest(UUID productId, Integer defaultQty) {}
