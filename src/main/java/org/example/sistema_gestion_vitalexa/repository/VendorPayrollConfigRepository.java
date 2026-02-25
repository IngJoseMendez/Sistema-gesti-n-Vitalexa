package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.entity.VendorPayrollConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorPayrollConfigRepository extends JpaRepository<VendorPayrollConfig, UUID> {

    Optional<VendorPayrollConfig> findByVendedor(User vendedor);

    Optional<VendorPayrollConfig> findByVendedorId(UUID vendedorId);

    boolean existsByVendedorId(UUID vendedorId);
}

