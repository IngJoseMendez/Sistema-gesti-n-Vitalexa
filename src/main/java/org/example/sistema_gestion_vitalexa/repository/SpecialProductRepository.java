package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.SpecialProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpecialProductRepository extends JpaRepository<SpecialProduct, UUID> {

  List<SpecialProduct> findByActiveTrue();

  Page<SpecialProduct> findByActiveTrue(Pageable pageable);

  List<SpecialProduct> findByParentProductId(UUID parentProductId);

  Page<SpecialProduct> findByParentProductId(UUID parentProductId, Pageable pageable);

  @Query("""
      SELECT sp FROM SpecialProduct sp
      WHERE sp.active = true
        AND (:q IS NULL OR :q = '' OR lower(sp.nombre) LIKE lower(concat('%', :q, '%')))
      """)
  Page<SpecialProduct> searchActive(@Param("q") String q, Pageable pageable);

  /**
   * Cuenta cuántos productos especiales activos están vinculados a un producto
   * padre.
   */
  long countByParentProductIdAndActiveTrue(UUID parentProductId);

  /**
   * Encuentra productos especiales activos asignados a un vendedor específico.
   */
  @Query("""
      SELECT DISTINCT sp FROM SpecialProduct sp
      JOIN sp.allowedVendors v
      WHERE sp.active = true AND v.id = :vendorId
      """)
  List<SpecialProduct> findActiveByVendorId(@Param("vendorId") UUID vendorId);

  @Query("""
      SELECT DISTINCT sp FROM SpecialProduct sp
      JOIN sp.allowedVendors v
      WHERE sp.active = true AND v.id = :vendorId
      """)
  Page<SpecialProduct> findActiveByVendorId(@Param("vendorId") UUID vendorId, Pageable pageable);
}
