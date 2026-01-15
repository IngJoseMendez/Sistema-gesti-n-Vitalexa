package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.ShoppingListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

    Optional<ShoppingListItem> findByIdAndListId(UUID id, UUID listId);

    Optional<ShoppingListItem> findByListIdAndProductId(UUID listId, UUID productId);
}

