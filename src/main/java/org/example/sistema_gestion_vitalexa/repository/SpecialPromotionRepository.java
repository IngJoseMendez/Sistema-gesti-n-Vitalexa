package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.SpecialPromotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecialPromotionRepository extends JpaRepository<SpecialPromotion, UUID> {

    @Query("SELECT sp FROM SpecialPromotion sp WHERE sp.active = true")
    Page<SpecialPromotion> findByActiveTrue(Pageable pageable);

    @Query("SELECT sp FROM SpecialPromotion sp WHERE sp.parentPromotion.id = :parentId")
    List<SpecialPromotion> findByParentPromotionId(@Param("parentId") UUID parentId);

    @Query("SELECT sp FROM SpecialPromotion sp WHERE sp.active = true AND " +
            "(LOWER(sp.nombre) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR (sp.parentPromotion IS NOT NULL AND LOWER(sp.parentPromotion.nombre) LIKE LOWER(CONCAT('%', :query, '%'))))")
    Page<SpecialPromotion> searchActive(@Param("query") String query, Pageable pageable);

    /**
     * Busca promociones especiales activas disponibles para un vendedor específico.
     * Retorna aquellas donde el vendedor está en la lista de allowedVendors.
     */
    @Query("SELECT sp FROM SpecialPromotion sp JOIN sp.allowedVendors u WHERE u.id = :vendorId AND sp.active = true")
    List<SpecialPromotion> findActiveByVendorId(@Param("vendorId") UUID vendorId);

    @Query("SELECT sp FROM SpecialPromotion sp JOIN sp.allowedVendors u WHERE u.id = :vendorId AND sp.active = true")
    Page<SpecialPromotion> findActiveByVendorId(@Param("vendorId") UUID vendorId, Pageable pageable);
}
