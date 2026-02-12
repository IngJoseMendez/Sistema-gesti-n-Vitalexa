package org.example.sistema_gestion_vitalexa.dto;

import java.util.List;
import java.util.UUID;

public record BulkProductUpdateResult(
        List<ProductResponse> successful,
        List<BulkError> failures) {

    public record BulkError(UUID id, String productName, String message) {
    }
}
