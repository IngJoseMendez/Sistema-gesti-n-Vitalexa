package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.Payroll;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, UUID> {

    Optional<Payroll> findByVendedorAndMonthAndYear(User vendedor, int month, int year);

    boolean existsByVendedorAndMonthAndYear(User vendedor, int month, int year);

    List<Payroll> findByVendedorOrderByYearDescMonthDesc(User vendedor);

    List<Payroll> findByMonthAndYear(int month, int year);

    List<Payroll> findAllByOrderByYearDescMonthDesc();

    @Query("SELECT p FROM Payroll p WHERE p.vendedor.id = :vendedorId ORDER BY p.year DESC, p.month DESC")
    List<Payroll> findByVendedorIdOrderByYearDescMonthDesc(@Param("vendedorId") UUID vendedorId);
}

