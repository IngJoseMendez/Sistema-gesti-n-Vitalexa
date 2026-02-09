package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.InventoryMovement;
import org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.UUID;

public interface InventoryMovementRepository
                extends JpaRepository<InventoryMovement, UUID>, JpaSpecificationExecutor<InventoryMovement> {

        Page<InventoryMovement> findByProductId(UUID productId, Pageable pageable);

        Page<InventoryMovement> findByType(InventoryMovementType type, Pageable pageable);

        // Buscar por rango de fechas
        Page<InventoryMovement> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

}
