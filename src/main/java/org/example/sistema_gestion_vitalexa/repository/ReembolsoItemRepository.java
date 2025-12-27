package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.ReembolsoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReembolsoItemRepository extends JpaRepository<ReembolsoItem, UUID> {
}
