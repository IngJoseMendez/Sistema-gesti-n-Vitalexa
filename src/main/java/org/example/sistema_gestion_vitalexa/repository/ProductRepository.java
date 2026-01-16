package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Listas (para endpoints existentes)
    List<Product> findByActiveTrue();

    List<Product> findByStockLessThanAndActiveTrue(int threshold);

    // Paginación (nuevo)
    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndStockGreaterThan(int stock, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND (:q IS NULL OR :q = '' OR lower(p.nombre) LIKE lower(concat('%', :q, '%')))
        """)
    Page<Product> searchActive(@Param("q") String q, Pageable pageable);

    // Tag filtering
    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true AND p.tag.id = :tagId
        """)
    Page<Product> findByTagId(@Param("tagId") UUID tagId, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true 
          AND p.tag.id = :tagId
          AND (:q IS NULL OR :q = '' OR lower(p.nombre) LIKE lower(concat('%', :q, '%')))
        """)
    Page<Product> searchByTagId(@Param("q") String q, @Param("tagId") UUID tagId, Pageable pageable);
}
