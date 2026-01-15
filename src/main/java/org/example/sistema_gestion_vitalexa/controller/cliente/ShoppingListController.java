package org.example.sistema_gestion_vitalexa.controller.cliente;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.service.ShoppingListService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cliente/lists")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    @PostMapping
    public ShoppingListResponse create(@RequestBody CreateShoppingListRequest request,
                                       java.security.Principal principal) {
        return shoppingListService.createList(principal.getName(), request);
    }

    @GetMapping
    public List<ShoppingListResponse> myLists(java.security.Principal principal) {
        return shoppingListService.myLists(principal.getName());
    }

    @PostMapping("/{id}/items")
    public ShoppingListResponse addItem(@PathVariable java.util.UUID id,
                                        @RequestBody ShoppingListItemRequest request,
                                        java.security.Principal principal) {
        return shoppingListService.addItem(principal.getName(), id, request);
    }

    @PatchMapping("/{id}/items/{itemId}")
    public ShoppingListResponse updateQty(@PathVariable java.util.UUID id,
                                          @PathVariable java.util.UUID itemId,
                                          @RequestBody UpdateShoppingListItemQtyRequest request,
                                          java.security.Principal principal) {
        return shoppingListService.updateItemQty(principal.getName(), id, itemId, request);
    }

    @PostMapping("/{id}/to-order")
    public OrderResponse toOrder(@PathVariable java.util.UUID id,
                                 java.security.Principal principal) {
        return shoppingListService.toOrder(principal.getName(), id);
    }
}

