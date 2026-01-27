package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

public record GiftItemDTO(
        UUID productId,
        Integer quantity) {
}
