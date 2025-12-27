package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.Reembolso;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReembolsoRepository extends JpaRepository<Reembolso, UUID> {
    List<Reembolso> findByEmpacadorOrderByFechaDesc(User empacador);
    List<Reembolso> findAllByOrderByFechaDesc();
}
