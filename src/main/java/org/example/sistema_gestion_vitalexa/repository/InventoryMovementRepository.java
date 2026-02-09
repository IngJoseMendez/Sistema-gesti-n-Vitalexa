package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.InventoryMovement;
import org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    Page<InventoryMovement> findByProductId(UUID productId, Pageable pageable);

    Page<InventoryMovement> findByType(InventoryMovementType type, Pageable pageable);

    // Buscar por rango de fechas
    Page<InventoryMovement> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Filtro multicriterio básico
    @Query("SELECT m FROM InventoryMovement m WHERE " +
            "(:productId IS NULL OR m.product.id = :productId) AND " +
            "(:type IS NULL OR m.type = :type) AND " +
            "(:startDate IS NULL OR m.timestamp >= :startDate) AND " +
            "(:endDate IS NULL OR m.timestamp <= :endDate)")
    Page<InventoryMovement> findWithFilters(
            @Param("productId") UUID productId,
            @Param("type") InventoryMovementType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Para exportación sin paginación (cuidado con volumen, pero el usuario pidió
    // "descargar todo")
    @Query("SELECT m FROM InventoryMovement m WHERE " +
            "(:productId IS NULL OR m.product.id = :productId) AND " +
            "(:type IS NULL OR m.type = :type) AND " +
            "(:startDate IS NULL OR m.timestamp >= :startDate) AND " +
            "(:endDate IS NULL OR m.timestamp <= :endDate) " +
            "ORDER BY m.timestamp DESC")
    List<InventoryMovement> findAllWithFilters(
            @Param("productId") UUID productId,
            @Param("type") InventoryMovementType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
