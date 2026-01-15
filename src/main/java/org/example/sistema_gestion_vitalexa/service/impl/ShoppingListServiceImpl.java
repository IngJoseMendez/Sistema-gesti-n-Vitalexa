package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.entity.*;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.OrderMapper;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.ShoppingListItemRepository;
import org.example.sistema_gestion_vitalexa.repository.ShoppingListRepository;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.example.sistema_gestion_vitalexa.service.ShoppingListService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@jakarta.transaction.Transactional
public class ShoppingListServiceImpl implements ShoppingListService {

    private final ClientRepository clientRepository;
    private final ShoppingListRepository listRepository;
    private final ShoppingListItemRepository itemRepository;

    private final ProductService productService;
    private final OrdenRepository ordenRepository;
    private final OrderMapper orderMapper;

    private Client getClientOrThrow(String username) {
        return clientRepository.findByUserUsername(username)
                .orElseThrow(() -> new BusinessExeption("Este usuario no tiene Client asociado"));
    }

    private ShoppingList getOwnedListOrThrow(String username, UUID listId) {
        Client client = getClientOrThrow(username);
        return listRepository.findByIdAndClientId(listId, client.getId())
                .orElseThrow(() -> new BusinessExeption("Lista no encontrada"));
    }

    private ShoppingListResponse toResponse(ShoppingList list) {
        List<ShoppingListItemResponse> items = list.getItems() == null ? List.of()
                : list.getItems().stream()
                .map(i -> new ShoppingListItemResponse(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getNombre(),
                        i.getDefaultQty()
                ))
                .toList();

        return new ShoppingListResponse(list.getId(), list.getName(), items);
    }

    @Override
    public ShoppingListResponse createList(String username, CreateShoppingListRequest request) {
        Client client = getClientOrThrow(username);

        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BusinessExeption("El nombre de la lista es obligatorio");
        }

        String name = request.name().trim();

        if (listRepository.existsByClientIdAndNameIgnoreCase(client.getId(), name)) {
            throw new BusinessExeption("Ya existe una lista con ese nombre");
        }

        ShoppingList list = ShoppingList.builder()
                .client(client)
                .name(name)
                .createdAt(java.time.LocalDateTime.now())
                .items(new java.util.ArrayList<>())
                .build();

        ShoppingList saved = listRepository.save(list);
        return toResponse(saved);
    }

    @Override
    public List<ShoppingListResponse> myLists(String username) {
        Client client = getClientOrThrow(username);

        return listRepository.findByClientId(client.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ShoppingListResponse addItem(String username, UUID listId, ShoppingListItemRequest request) {
        ShoppingList list = getOwnedListOrThrow(username, listId);

        if (request == null || request.productId() == null) {
            throw new BusinessExeption("productId es obligatorio");
        }
        if (request.defaultQty() == null || request.defaultQty() < 1) {
            throw new BusinessExeption("La cantidad debe ser mayor o igual a 1");
        }

        Product product = productService.findEntityById(request.productId());
        if (!product.isActive()) {
            throw new BusinessExeption("Producto inactivo");
        }

        // Upsert: si ya existe el producto en la lista, actualiza qty
        ShoppingListItem item = itemRepository.findByListIdAndProductId(list.getId(), product.getId())
                .orElse(null);

        if (item == null) {
            item = ShoppingListItem.builder()
                    .list(list)
                    .product(product)
                    .defaultQty(request.defaultQty())
                    .build();
            list.getItems().add(item);
        } else {
            item.setDefaultQty(request.defaultQty());
        }

        listRepository.save(list); // cascade guarda items
        return toResponse(list);
    }

    @Override
    public ShoppingListResponse updateItemQty(String username, UUID listId, UUID itemId, UpdateShoppingListItemQtyRequest request) {
        ShoppingList list = getOwnedListOrThrow(username, listId);

        if (request == null || request.defaultQty() == null || request.defaultQty() < 1) {
            throw new BusinessExeption("La cantidad debe ser mayor o igual a 1");
        }

        ShoppingListItem item = itemRepository.findByIdAndListId(itemId, list.getId())
                .orElseThrow(() -> new BusinessExeption("Item no encontrado en esta lista"));

        item.setDefaultQty(request.defaultQty());
        itemRepository.save(item);

        return toResponse(list);
    }

    @Override
    public OrderResponse toOrder(String username, UUID listId) {
        Client client = getClientOrThrow(username);

        // Traer lista con items + product
        ShoppingList list = listRepository.findWithItemsByIdAndClientId(listId, client.getId())
                .orElseThrow(() -> new BusinessExeption("Lista no encontrada"));

        if (list.getItems() == null || list.getItems().isEmpty()) {
            throw new BusinessExeption("La lista no tiene productos");
        }

        if (client.getVendedorAsignado() == null) {
            throw new BusinessExeption("Este cliente no tiene vendedor asignado");
        }

        // 1) Validar stock (fallar completo si alguno no alcanza)
        for (ShoppingListItem it : list.getItems()) {
            Product p = it.getProduct();
            if (!p.isActive()) throw new BusinessExeption("Producto inactivo: " + p.getNombre());
            if (p.getStock() < it.getDefaultQty()) {
                throw new BusinessExeption("Stock insuficiente para: " + p.getNombre()
                        + " (Disponible: " + p.getStock() + ", Pedido: " + it.getDefaultQty() + ")");
            }
        }

        // 2) Crear orden y descontar stock
        Order order = new Order(client.getVendedorAsignado(), client);
        order.setNotas("Orden generada desde lista: " + list.getName());

        for (ShoppingListItem it : list.getItems()) {
            Product p = it.getProduct();
            int qty = it.getDefaultQty();

            p.decreaseStock(qty);

            OrderItem orderItem = new OrderItem(p, qty);
            order.addItem(orderItem);
        }

        Order saved = ordenRepository.save(order);

        // Mantengo tu comportamiento actual: sumar compra al crear orden
        client.registerPurchase(saved.getTotal());
        clientRepository.save(client);

        return orderMapper.toResponse(saved);
    }
}

