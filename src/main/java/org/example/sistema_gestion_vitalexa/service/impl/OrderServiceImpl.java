package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.OrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.entity.*;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.OrderMapper;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.example.sistema_gestion_vitalexa.service.impl.ProductServiceImpl.log;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrdenService {

    private final OrdenRepository ordenRepository;
    private final ProductService productService;
    private final ClientService clientService;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;
    private final SaleGoalService saleGoalService;

    // =========================
    // CREATE ORDER (VENDEDOR)
    // =========================
    @Override
    public OrderResponse createOrder(OrderRequestDto request, String username) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessExeption("La venta debe tener al menos un producto");
        }

        User vendedor = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

        Client client = null;
        if (request.clientId() != null) {
            client = clientService.findEntityById(request.clientId());
        }

        Order order = new Order(vendedor, client);

        // Agregar notas si existen
        if (request.notas() != null && !request.notas().isBlank()) {
            order.setNotas(request.notas());
        }

        request.items().forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());

            // Permitir productos sin stock si hay una nota
            if (product.getStock() < itemReq.cantidad() &&
                    (request.notas() == null || request.notas().isBlank())) {
                throw new BusinessExeption("Stock insuficiente para: " + product.getNombre());
            }

            // Solo decrementar stock si hay suficiente
            if (product.getStock() >= itemReq.cantidad()) {
                product.decreaseStock(itemReq.cantidad());
            }

            OrderItem item = new OrderItem(product, itemReq.cantidad());
            order.addItem(item);
        });

        Order savedOrder = ordenRepository.save(order);

        // ✅ ENVIAR NOTIFICACIÓN DE NUEVA ORDEN
        notificationService.sendNewOrderNotification(
                savedOrder.getId().toString(),
                vendedor.getUsername(),
                client != null ? client.getNombre() : "Sin cliente"
        );

        if (client != null) {
            client.registerPurchase(savedOrder.getTotal());
        }

        return orderMapper.toResponse(savedOrder);
    }


    // =========================
    // ADMIN / OWNER
    // =========================
    @Override
    public List<OrderResponse> findAll() {
        return ordenRepository.findAll()
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    public OrderResponse findById(UUID id) {
        Order order = ordenRepository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));
        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponse cambiarEstadoOrden(UUID id, OrdenStatus nuevoEstado) {
        Order order = ordenRepository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        OrdenStatus oldStatus = order.getEstado();
        order.setEstado(nuevoEstado);
        Order updated = ordenRepository.save(order);

        // ✅ ENVIAR NOTIFICACIÓN SI SE COMPLETA
        if (nuevoEstado == OrdenStatus.COMPLETADO && oldStatus != OrdenStatus.COMPLETADO) {
            notificationService.sendOrderCompletedNotification(id.toString());
            log.info("Orden {} completada, notificación enviada", id);

            // ✅ ACTUALIZAR PROGRESO DE META DEL VENDEDOR
            LocalDate fecha = order.getFecha().toLocalDate();
            saleGoalService.updateGoalProgress(
                    order.getVendedor().getId(),
                    order.getTotal(),
                    fecha.getMonthValue(),
                    fecha.getYear()
            );
        }

        if (nuevoEstado == OrdenStatus.COMPLETADO) {
            notificationService.sendOrderCompletedNotification(order.getId().toString());
        }

        // 🔔 NOTIFICAR CAMBIO DE INVENTARIO (las órdenes también afectan stock)
        notificationService.sendInventoryUpdate(order.getId().toString().toString(), "ORDER_STATUS_CHANGED");

        return orderMapper.toResponse(updated);
    }

    // =========================
    // VENDEDOR (SEGURIDAD REAL)
    // =========================
    @Override
    public List<OrderResponse> findMyOrders(String username) {

        User vendedor = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        return ordenRepository.findByVendedor(vendedor)
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    public OrderResponse findMyOrderById(UUID id, String username) {

        Order order = ordenRepository
                .findByIdAndVendedorUsername(id, username)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(UUID orderId, OrderRequestDto request) {
        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        if (order.getEstado() == OrdenStatus.COMPLETADO ||
                order.getEstado() == OrdenStatus.CANCELADO) {
            throw new BusinessExeption("No se puede editar una orden completada o cancelada");
        }

        // ✅ VALIDAR QUE HAYA ITEMS
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessExeption("La orden debe tener al menos un producto");
        }

        // ✅ RESTAURAR STOCK de items anteriores
        order.getItems().forEach(item -> {
            Product product = item.getProduct();
            product.increaseStock(item.getCantidad());
        });

        // Limpiar items actuales
        order.clearItems();

        // ✅ AGREGAR NUEVOS ITEMS (con validación de stock)
        request.items().forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());

            // Validar stock disponible
            if (product.getStock() < itemReq.cantidad()) {
                throw new BusinessExeption("Stock insuficiente para: " + product.getNombre() +
                        " (Disponible: " + product.getStock() + ", Solicitado: " + itemReq.cantidad() + ")");
            }

            // Decrementar stock
            product.decreaseStock(itemReq.cantidad());

            // Agregar item a la orden
            OrderItem item = new OrderItem(product, itemReq.cantidad());
            order.addItem(item);
        });

        // Actualizar notas
        order.setNotas(request.notas());

        // Actualizar cliente
        if (request.clientId() != null) {
            Client newClient = clientService.findEntityById(request.clientId());
            order.setCliente(newClient);
        } else {
            order.setCliente(null);
        }

        // Guardar orden actualizada
        Order updatedOrder = ordenRepository.save(order);
        return orderMapper.toResponse(updatedOrder);
    }


    @Override
    public List<OrderResponse> findByEstado(OrdenStatus estado) {
        return ordenRepository.findByEstado(estado)
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }



}
