package org.example.sistema_gestion_vitalexa.dto;

import java.util.List;
import java.util.UUID;

public record StockArrivalItemDTO(
        UUID productId,
        Integer quantity) {
}
