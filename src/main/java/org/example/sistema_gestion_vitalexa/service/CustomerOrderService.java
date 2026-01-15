package org.example.sistema_gestion_vitalexa.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CustomerOrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.entity.*;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.OrderMapper;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerOrderService {

    private final ClientRepository clientRepository;
    private final OrdenRepository ordenRepository;
    private final ProductService productService;
    private final OrderMapper orderMapper;

    private Client getClientOrThrow(String username) {
        Client client = clientRepository.findByUserUsername(username)
                .orElseThrow(() -> new BusinessExeption("Este usuario no tiene Client asociado"));
        if (!client.isActive()) throw new BusinessExeption("Cliente inactivo");
        return client;
    }

    private Order getOwnedOrderOrThrow(UUID orderId, Client client) {
        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));
        if (order.getCliente() == null || !order.getCliente().getId().equals(client.getId())) {
            throw new BusinessExeption("No tienes acceso a esta orden");
        }
        return order;
    }

    public OrderResponse create(String username, CustomerOrderRequestDto request) {
        Client client = getClientOrThrow(username);

        User vendedor = client.getVendedorAsignado();
        if (vendedor == null) throw new BusinessExeption("Cliente sin vendedor asignado");

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessExeption("La orden debe tener al menos un producto");
        }

        Order order = new Order(vendedor, client);

        if (request.notas() != null && !request.notas().isBlank()) {
            order.setNotas(request.notas());
        }

        request.items().forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());
            if (!product.isActive()) throw new BusinessExeption("Producto inactivo: " + product.getNombre());
            if (product.getStock() < itemReq.cantidad()) {
                throw new BusinessExeption("Stock insuficiente para: " + product.getNombre());
            }
            product.decreaseStock(itemReq.cantidad());
            order.addItem(new OrderItem(product, itemReq.cantidad()));
        });

        Order saved = ordenRepository.save(order);

        client.registerPurchase(saved.getTotal());
        clientRepository.save(client);

        return orderMapper.toResponse(saved);
    }

    public List<OrderResponse> myOrders(String username) {
        Client client = getClientOrThrow(username);
        return ordenRepository.findByCliente(client).stream().map(orderMapper::toResponse).toList();
    }

    public OrderResponse myOrderDetail(String username, UUID orderId) {
        Client client = getClientOrThrow(username);
        Order order = getOwnedOrderOrThrow(orderId, client);
        return orderMapper.toResponse(order);
    }

    public OrderResponse cancel(String username, UUID orderId) {
        Client client = getClientOrThrow(username);
        Order order = getOwnedOrderOrThrow(orderId, client);

        if (order.getEstado() == OrdenStatus.COMPLETADO) throw new BusinessExeption("No se puede cancelar una orden completada");
        if (order.getEstado() == OrdenStatus.CANCELADO) return orderMapper.toResponse(order);

        // opcional: devolver stock si quieres comportamiento “pro”
        order.getItems().forEach(i -> i.getProduct().increaseStock(i.getCantidad()));

        order.setEstado(OrdenStatus.CANCELADO);
        Order saved = ordenRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    public OrderResponse reorder(String username, UUID orderId) {
        Client client = getClientOrThrow(username);
        Order original = getOwnedOrderOrThrow(orderId, client);

        User vendedor = client.getVendedorAsignado();
        if (vendedor == null) throw new BusinessExeption("Cliente sin vendedor asignado");

        Order copy = new Order(vendedor, client);
        copy.setNotas("Reorden de: " + original.getId().toString().substring(0, 8));

        original.getItems().forEach(oldItem -> {
            Product product = oldItem.getProduct();
            int qty = oldItem.getCantidad();

            if (!product.isActive()) throw new BusinessExeption("Producto inactivo: " + product.getNombre());
            if (product.getStock() < qty) throw new BusinessExeption("Stock insuficiente para reordenar: " + product.getNombre());

            product.decreaseStock(qty);
            copy.addItem(new OrderItem(product, qty));
        });

        Order saved = ordenRepository.save(copy);

        client.registerPurchase(saved.getTotal());
        clientRepository.save(client);

        return orderMapper.toResponse(saved);
    }
}


