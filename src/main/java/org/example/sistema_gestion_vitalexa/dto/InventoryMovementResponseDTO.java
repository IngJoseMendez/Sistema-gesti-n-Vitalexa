package org.example.sistema_gestion_vitalexa.dto;

import lombok.Builder;
import lombok.Data;
import org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class InventoryMovementResponseDTO {
    private UUID id;
    private UUID productId;
    private String productName;
    private InventoryMovementType type;
    private Integer quantity;
    private Integer previousStock;
    private Integer newStock;
    private String reason;
    private String username;
    private LocalDateTime timestamp;
}
