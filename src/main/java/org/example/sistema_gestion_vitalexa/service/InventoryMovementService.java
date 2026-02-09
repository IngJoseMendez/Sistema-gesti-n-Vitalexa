package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.InventoryMovementResponseDTO;
import org.example.sistema_gestion_vitalexa.entity.InventoryMovement;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface InventoryMovementService {

        void logMovement(Product product, InventoryMovementType type, Integer quantity, Integer previousStock,
                        Integer newStock, String reason, String username);

        Page<InventoryMovementResponseDTO> getHistory(UUID productId, InventoryMovementType type,
                        LocalDateTime startDate,
                        LocalDateTime endDate, Pageable pageable);

        byte[] generateHistoryPdf(List<InventoryMovement> movements, String username, String filterDescription);

        // Para exportar todo (sin paginación)
        List<InventoryMovement> getAllHistory(UUID productId, InventoryMovementType type, LocalDateTime startDate,
                        LocalDateTime endDate);

        InventoryMovement findById(UUID id);
}
