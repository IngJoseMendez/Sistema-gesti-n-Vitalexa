package org.example.sistema_gestion_vitalexa.dto;

import java.util.List;

public record BulkStockArrivalRequestDTO(
        String reason,
        List<StockArrivalItemDTO> items) {
}
