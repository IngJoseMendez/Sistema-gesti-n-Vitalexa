package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.*;

import java.util.List;
import java.util.UUID;

public interface ShoppingListService {
    ShoppingListResponse createList(String username, CreateShoppingListRequest request);
    List<ShoppingListResponse> myLists(String username);
    ShoppingListResponse addItem(String username, UUID listId, ShoppingListItemRequest request);
    ShoppingListResponse updateItemQty(String username, UUID listId, UUID itemId, UpdateShoppingListItemQtyRequest request);
    OrderResponse toOrder(String username, UUID listId);
}

