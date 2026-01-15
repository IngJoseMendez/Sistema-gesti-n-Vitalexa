package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {

    List<ShoppingList> findByClientId(UUID clientId);

    boolean existsByClientIdAndNameIgnoreCase(UUID clientId, String name);

    Optional<ShoppingList> findByIdAndClientId(UUID id, UUID clientId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<ShoppingList> findWithItemsByIdAndClientId(UUID id, UUID clientId);
}

