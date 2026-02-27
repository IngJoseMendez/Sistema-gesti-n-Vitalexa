package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    // Encontrar promociones activas
    List<Promotion> findByActiveTrue();

    // Encontrar promociones válidas (activas y dentro del período)
    @Query("SELECT p FROM Promotion p WHERE p.active = true " +
            "AND (p.validFrom IS NULL OR p.validFrom <= :now) " +
            "AND (p.validUntil IS NULL OR p.validUntil >= :now)")
    List<Promotion> findValidPromotions(@Param("now") LocalDateTime now);

    /**
     * Versión optimizada con FETCH JOIN para cargar giftItems y mainProduct
     * en UNA SOLA query, evitando el problema N+1 que hace la página lenta.
     * Usar DISTINCT para evitar filas duplicadas por el JOIN con giftItems.
     */
    @Query("SELECT DISTINCT p FROM Promotion p " +
            "LEFT JOIN FETCH p.giftItems gi " +
            "LEFT JOIN FETCH gi.product " +
            "LEFT JOIN FETCH p.mainProduct " +
            "WHERE p.active = true " +
            "AND (p.validFrom IS NULL OR p.validFrom <= :now) " +
            "AND (p.validUntil IS NULL OR p.validUntil >= :now)")
    List<Promotion> findValidPromotionsEager(@Param("now") LocalDateTime now);

    // Encontrar por producto principal
    List<Promotion> findByMainProductId(UUID productId);
}
