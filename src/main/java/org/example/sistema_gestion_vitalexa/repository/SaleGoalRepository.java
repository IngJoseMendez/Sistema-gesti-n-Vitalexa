package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.SaleGoal;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SaleGoalRepository extends JpaRepository<SaleGoal, UUID> {

    /**
     * Buscar meta de un vendedor en un mes/año específico
     */
    Optional<SaleGoal> findByVendedorAndMonthAndYear(User vendedor, int month, int year);

    /**
     * Buscar todas las metas de un vendedor
     */
    List<SaleGoal> findByVendedorOrderByYearDescMonthDesc(User vendedor);

    /**
     * Buscar todas las metas de un mes/año específico
     */
    List<SaleGoal> findByMonthAndYear(int month, int year);

    /**
     * Verificar si existe una meta para vendedor/mes/año
     */
    boolean existsByVendedorAndMonthAndYear(User vendedor, int month, int year);

    /**
     * Buscar todas las metas ordenadas por fecha
     */
    List<SaleGoal> findAllByOrderByYearDescMonthDesc();
}
