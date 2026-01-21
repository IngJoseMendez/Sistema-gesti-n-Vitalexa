package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.AddAssortmentItemRequest;
import org.example.sistema_gestion_vitalexa.dto.OrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.dto.OrderItemRequestDTO;
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

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderServiceImpl implements OrdenService {

    private final OrdenRepository ordenRepository;
    private final ProductService productService;
    private final ClientService clientService;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;
    private final SaleGoalService saleGoalService;
    private final ProductTagService productTagService;
    private final PromotionService promotionService;

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

        // Validar tope de crédito del cliente
        if (client != null && client.getCreditLimit() != null) {
            // Calcular el total de la venta antes de crearla
            BigDecimal saleTotal = request.items().stream()
                    .map(item -> {
                        Product p = productService.findEntityById(item.productId());
                        return p.getPrecio().multiply(BigDecimal.valueOf(item.cantidad()));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (saleTotal.compareTo(client.getCreditLimit()) > 0) {
                throw new BusinessExeption("El valor de la venta ($" + saleTotal +
                        ") excede el tope de crédito del cliente ($" + client.getCreditLimit() + ")");
            }
        }

        // Obtener etiqueta del sistema "S/R" (puede no existir aún)
        ProductTag srTag = null;
        try {
            srTag = productTagService.getSRTagEntity();
        } catch (Exception e) {
            log.warn("Etiqueta del sistema S/R no encontrada, continuando sin split");
        }

        // Separar items en dos listas: con tag S/R y sin tag S/R
        List<OrderItemRequestDTO> normalItems = new java.util.ArrayList<>();
        List<OrderItemRequestDTO> srItems = new java.util.ArrayList<>();

        ProductTag finalSrTag = srTag;
        request.items().forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());
            boolean isSRProduct = finalSrTag != null && product.getTag() != null
                    && product.getTag().getId().equals(finalSrTag.getId());

            if (isSRProduct) {
                srItems.add(itemReq);
            } else {
                normalItems.add(itemReq);
            }
        });

        // Decidir si necesitamos split
        boolean needsSplit = srTag != null && !srItems.isEmpty();

        if (!needsSplit) {
            // Caso normal: crear una sola orden sin productos S/R
            return createSingleOrder(vendedor, client, request, username);
        } else if (normalItems.isEmpty()) {
            // Caso especial: solo hay productos S/R
            OrderRequestDto srOnlyRequest = new OrderRequestDto(
                    request.clientId(),
                    srItems,
                    request.notas(),
                    request.promotionIds());
            return createSingleOrder(vendedor, client, srOnlyRequest, username);
        } else {
            // Caso split: crear dos órdenes con números de factura consecutivos
            // Esto debe ser atómico
            return createSplitOrders(vendedor, client, normalItems, srItems, request.notas(), username);
        }
    }

    /**
     * Crear una sola orden (sin split o solo S/R)
     */
    private OrderResponse createSingleOrder(User vendedor, Client client, OrderRequestDto request, String username) {
        Order order = new Order(vendedor, client);

        if (request.notas() != null && !request.notas().isBlank()) {
            order.setNotas(request.notas());
        }

        processOrderItems(order, request.items());

        Order savedOrder = ordenRepository.save(order);

        notificationService.sendNewOrderNotification(
                savedOrder.getId().toString(),
                vendedor.getUsername(),
                client != null ? client.getNombre() : "Sin cliente");

        if (client != null) {
            client.registerPurchase(savedOrder.getTotal());
        }

        return orderMapper.toResponse(savedOrder);
    }

    /**
     * Crear dos órdenes con split y números de factura consecutivos
     */
    private OrderResponse createSplitOrders(
            User vendedor,
            Client client,
            List<OrderItemRequestDTO> normalItems,
            List<OrderItemRequestDTO> srItems,
            String notas,
            String username) {
        // Crear orden normal
        Order normalOrder = new Order(vendedor, client);
        if (notas != null && !notas.isBlank()) {
            normalOrder.setNotas(notas + "[Normal]");
        }
        processOrderItems(normalOrder, normalItems);

        // Crear orden S/R
        Order srOrder = new Order(vendedor, client);
        srOrder.setNotas(notas + "[S/R]");
        processOrderItems(srOrder, srItems);

        // Guardar normal order primero para obtener número de factura
        Order savedNormalOrder = ordenRepository.save(normalOrder);

        // Guardar SR order
        Order savedSROrder = ordenRepository.save(srOrder);

        // Ambas órdenes se tratan como "reales" en términos de facturación
        // Los números de factura se asignan cuando se completen

        notificationService.sendNewOrderNotification(
                savedNormalOrder.getId().toString(),
                vendedor.getUsername(),
                client != null ? client.getNombre() : "Sin cliente");

        notificationService.sendNewOrderNotification(
                savedSROrder.getId().toString(),
                vendedor.getUsername(),
                client != null ? client.getNombre() : "Sin cliente");

        if (client != null) {
            client.registerPurchase(savedNormalOrder.getTotal().add(savedSROrder.getTotal()));
        }

        // Log para auditoria
        log.info("Orden dividida: Normal ({}) + S/R ({}) por vendedor {}",
                savedNormalOrder.getId(), savedSROrder.getId(), username);

        // Retornamos la orden normal como respuesta principal
        // El cliente puede consultar ambas órdenes luego
        return orderMapper.toResponse(savedNormalOrder);
    }

    /**
     * Procesar items de una orden (decrementar stock, etc)
     */
    private void processOrderItems(Order order, List<OrderItemRequestDTO> items) {
        items.forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());

            boolean allowOutOfStock = Boolean.TRUE.equals(itemReq.allowOutOfStock());
            boolean hasStock = product.getStock() >= itemReq.cantidad();

            // Validar stock solo si NO se permite venta sin stock
            if (!allowOutOfStock && !hasStock) {
                throw new BusinessExeption("Stock insuficiente para: " + product.getNombre());
            }

            // Crear OrderItem
            OrderItem item = new OrderItem(product, itemReq.cantidad());

            // Marcar como sin stock si corresponde
            if (!hasStock) {
                item.setOutOfStock(true);
                log.warn("Producto agregado sin stock: {}", product.getNombre());
            }

            // Decrementar stock solo si hay disponible
            if (hasStock) {
                product.decreaseStock(itemReq.cantidad());
            }

            order.addItem(item);
        });
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

        // Si no cambia el estado, no hagas nada
        if (oldStatus == nuevoEstado) {
            return orderMapper.toResponse(order);
        }

        // Asignar invoiceNumber SOLO cuando hay transición real a COMPLETADO
        if (nuevoEstado == OrdenStatus.COMPLETADO && oldStatus != OrdenStatus.COMPLETADO) {

            if (order.getInvoiceNumber() == null) {
                Long nextInvoice = ordenRepository.nextInvoiceNumber();
                order.setInvoiceNumber(nextInvoice);
            }

            order.setEstado(OrdenStatus.COMPLETADO);

            // Actualizar progreso de meta del vendedor
            LocalDate fecha = order.getFecha().toLocalDate();
            saleGoalService.updateGoalProgress(
                    order.getVendedor().getId(),
                    order.getTotal(),
                    fecha.getMonthValue(),
                    fecha.getYear());

            // Notificación de orden completada (una sola vez)
            notificationService.sendOrderCompletedNotification(order.getId().toString());
            log.info("Orden {} completada (invoiceNumber={})", order.getId(), order.getInvoiceNumber());
        } else {
            // Otros estados
            order.setEstado(nuevoEstado);
        }

        // Guardar cambios
        Order updated = ordenRepository.save(order);

        // Notificar cambio de inventario/estado (una sola vez)
        notificationService.sendInventoryUpdate(order.getId().toString(), "ORDER_STATUS_CHANGED");

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

        // VALIDAR QUE HAYA ITEMS
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessExeption("La orden debe tener al menos un producto");
        }

        // RESTAURAR STOCK de items anteriores
        order.getItems().forEach(item -> {
            Product product = item.getProduct();
            product.increaseStock(item.getCantidad());
        });

        // Limpiar items actuales
        order.clearItems();

        // AGREGAR NUEVOS ITEMS (con validación de stock)
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

    // =========================
    // PROMOCIONES - SURTIDOS
    // =========================
    @Override
    @Transactional
    public void addPromotionAssortment(UUID orderId, UUID promotionId, List<AddAssortmentItemRequest> items) {
        log.info("Admin agregando surtidos a orden {} para promoción {}", orderId, promotionId);

        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        Promotion promotion = promotionService.findEntityById(promotionId);

        // Validar que la orden esté en estado PENDING_PROMOTION_COMPLETION
        if (order.getEstado() != OrdenStatus.PENDING_PROMOTION_COMPLETION) {
            throw new BusinessExeption("La orden no está esperando selección de surtidos");
        }

        // Validar cantidad total de surtidos
        int totalAssortment = items.stream().mapToInt(AddAssortmentItemRequest::cantidad).sum();
        if (totalAssortment != promotion.getFreeQuantity()) {
            throw new BusinessExeption("Debe seleccionar exactamente " +
                    promotion.getFreeQuantity() + " productos surtidos");
        }

        // Agregar cada producto surtido como OrderItem
        items.forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());

            // VALIDAR STOCK (advertencia pero no bloquear)
            if (product.getStock() < itemReq.cantidad()) {
                log.warn("Producto surtido {} no tiene stock suficiente. Stock: {}, Requerido: {}",
                        product.getNombre(), product.getStock(), itemReq.cantidad());
            }

            // Crear OrderItem con precio 0 (gratis/bonificado)
            OrderItem assortmentItem = OrderItem.builder()
                    .product(product)
                    .cantidad(itemReq.cantidad())
                    .precioUnitario(BigDecimal.ZERO) // GRATIS
                    .subTotal(BigDecimal.ZERO)
                    .promotion(promotion)
                    .isPromotionItem(true)
                    .isFreeItem(true)
                    .outOfStock(product.getStock() < itemReq.cantidad()) // Marcar si no hay stock
                    .build();

            order.addItem(assortmentItem);

            // Decrementar stock si hay disponible
            if (product.getStock() >= itemReq.cantidad()) {
                product.decreaseStock(itemReq.cantidad());
            } else {
                log.warn("Stock insuficiente para producto surtido {}, se marcó como sin stock", product.getNombre());
            }
        });

        // Cambiar estado de orden a CONFIRMADO
        order.setEstado(OrdenStatus.CONFIRMADO);

        ordenRepository.save(order);

        log.info("Productos surtidos agregados exitosamente a orden {}", orderId);
    }

}
