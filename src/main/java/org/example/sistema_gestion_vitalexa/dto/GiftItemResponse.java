package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

public record GiftItemResponse(
        UUID id,
        ProductResponse product,
        Integer quantity) {
}
