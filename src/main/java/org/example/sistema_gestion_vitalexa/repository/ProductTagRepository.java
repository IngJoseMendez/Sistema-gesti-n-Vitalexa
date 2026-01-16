package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.ProductTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductTagRepository extends JpaRepository<ProductTag, UUID> {

    /**
     * Buscar etiqueta por nombre (case-insensitive)
     */
    Optional<ProductTag> findByNameIgnoreCase(String name);

    /**
     * Obtener la etiqueta del sistema "S/R"
     */
    @Query("SELECT pt FROM ProductTag pt WHERE pt.isSystem = true AND UPPER(pt.name) = 'S/R'")
    Optional<ProductTag> findSRTag();

    /**
     * Obtener todas las etiquetas no del sistema
     */
    List<ProductTag> findByIsSystemFalse();

    /**
     * Obtener todas las etiquetas (incluyendo del sistema)
     */
    List<ProductTag> findAll();
}

