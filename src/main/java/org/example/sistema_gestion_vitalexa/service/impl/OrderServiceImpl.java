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
import org.example.sistema_gestion_vitalexa.repository.OrdenItemRepository;
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
    private final OrdenItemRepository ordenItemRepository;
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
    // =========================
    // CREATE ORDER (VENDEDOR)
    // =========================
    @Override
    public OrderResponse createOrder(OrderRequestDto request, String username) {
        // Validar que haya al menos items O promociones
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        boolean hasPromotions = request.promotionIds() != null && !request.promotionIds().isEmpty();

        if (!hasItems && !hasPromotions) {
            throw new BusinessExeption("La venta debe tener al menos un producto o una promoción");
        }

        User vendedor = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

        Client client = null;
        if (request.clientId() != null) {
            client = clientService.findEntityById(request.clientId());

            // Validate vendedor can access this client
            if (!clientService.canVendedorAccessClient(vendedor.getId(), request.clientId())) {
                throw new BusinessExeption("No tiene permiso para vender a este cliente");
            }
        }

        // Validar tope de crédito del cliente
        if (client != null && client.getCreditLimit() != null) {
            // Calcular el total de la venta antes de crearla
            BigDecimal saleTotal = BigDecimal.ZERO;

            if (request.items() != null) {
                BigDecimal itemsTotal = request.items().stream()
                        .map(item -> {
                            Product p = productService.findEntityById(item.productId());
                            return p.getPrecio().multiply(BigDecimal.valueOf(item.cantidad()));
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                saleTotal = saleTotal.add(itemsTotal);
            }

            // Nota: El cálculo de total de promociones es complejo sin procesarlas,
            // pero idealmente debería sumarse aquí. Por ahora mantenemos la lógica de
            // items.
            // Si las promociones tienen precio (packPrice), se debería sumar.

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

        // Separar items en listas: Normales y S/R
        List<OrderItemRequestDTO> normalItems = new java.util.ArrayList<>();
        List<OrderItemRequestDTO> srItems = new java.util.ArrayList<>();

        ProductTag finalSrTag = srTag;
        if (request.items() != null) {
            request.items().forEach(itemReq -> {
                Product product = productService.findEntityById(itemReq.productId());
                // Validamos si es SR
                boolean isSRProduct = finalSrTag != null && product.getTag() != null
                        && product.getTag().getId().equals(finalSrTag.getId());

                if (isSRProduct) {
                    srItems.add(itemReq);
                } else {
                    normalItems.add(itemReq);
                }
            });
        }

        List<UUID> promotionIds = request.promotionIds() != null ? request.promotionIds() : new java.util.ArrayList<>();

        // Identificar qué tipos de órdenes necesitamos crear
        boolean hasNormal = !normalItems.isEmpty();
        boolean hasSR = !srItems.isEmpty();
        boolean hasPromo = !promotionIds.isEmpty();

        int typesCount = (hasNormal ? 1 : 0) + (hasSR ? 1 : 0) + (hasPromo ? 1 : 0);

        if (typesCount <= 1) {
            // Caso simple: Crear una sola orden con lo que haya
            return createSingleOrder(vendedor, client, request, username);
        } else {
            // Caso múltiple: Crear órdenes separadas
            return createMultipleOrders(vendedor, client, normalItems, srItems, promotionIds, request.notas(),
                    username);
        }
    }

    /**
     * Crear órdenes separadas por tipo (Normal, S/R, Promo)
     */
    private OrderResponse createMultipleOrders(
            User vendedor,
            Client client,
            List<OrderItemRequestDTO> normalItems,
            List<OrderItemRequestDTO> srItems,
            List<UUID> promotionIds,
            String notas,
            String username) {

        OrderResponse response = null;
        BigDecimal totalPurchase = BigDecimal.ZERO;
        StringBuilder logMsg = new StringBuilder("Orden dividida en: ");

        // 1. ORDEN NORMAL
        if (!normalItems.isEmpty()) {
            Order normalOrder = new Order(vendedor, client);
            if (notas != null && !notas.isBlank()) {
                normalOrder.setNotas(notas + " [Normal]");
            }
            processOrderItems(normalOrder, normalItems);
            Order saved = ordenRepository.save(normalOrder);

            notificationService.sendNewOrderNotification(saved.getId().toString(), vendedor.getUsername(),
                    client != null ? client.getNombre() : "Sin cliente");

            totalPurchase = totalPurchase.add(saved.getTotal());
            response = orderMapper.toResponse(saved); // Prioridad 1 para response
            logMsg.append("Normal (").append(saved.getId()).append(") ");
        }

        // 2. ORDEN S/R
        if (!srItems.isEmpty()) {
            Order srOrder = new Order(vendedor, client);
            // Si solo hay S/R y Promos, pero no normal, y tenemos notas, aseguramos el tag
            // S/R
            String suffix = " [S/R]";
            srOrder.setNotas((notas != null ? notas : "") + suffix);

            processOrderItems(srOrder, srItems);
            Order saved = ordenRepository.save(srOrder);

            notificationService.sendNewOrderNotification(saved.getId().toString(), vendedor.getUsername(),
                    client != null ? client.getNombre() : "Sin cliente");

            totalPurchase = totalPurchase.add(saved.getTotal());
            if (response == null)
                response = orderMapper.toResponse(saved); // Prioridad 2
            logMsg.append("S/R (").append(saved.getId()).append(") ");
        }

        // 3. ORDEN PROMOCIONES
        if (!promotionIds.isEmpty()) {
            Order promoOrder = new Order(vendedor, client);
            String suffix = " [Promoción]";
            promoOrder.setNotas((notas != null ? notas : "") + suffix);

            processPromotions(promoOrder, promotionIds);
            Order saved = ordenRepository.save(promoOrder);

            notificationService.sendNewOrderNotification(saved.getId().toString(), vendedor.getUsername(),
                    client != null ? client.getNombre() : "Sin cliente");

            totalPurchase = totalPurchase.add(saved.getTotal());
            if (response == null)
                response = orderMapper.toResponse(saved); // Prioridad 3
            logMsg.append("Promo (").append(saved.getId()).append(") ");
        }

        // Registrar compra total en cliente
        if (client != null) {
            client.registerPurchase(totalPurchase);
        }

        log.info("{} por vendedor {}", logMsg.toString(), username);

        return response;
    }

    /**
     * Crear una sola orden (sin split o solo S/R)
     */
    private OrderResponse createSingleOrder(User vendedor, Client client, OrderRequestDto request, String username) {
        Order order = new Order(vendedor, client);

        if (request.notas() != null && !request.notas().isBlank()) {
            order.setNotas(request.notas());
        }

        // Procesar items regulares si existen
        if (request.items() != null && !request.items().isEmpty()) {
            processOrderItems(order, request.items());
        }

        // Procesar promociones si existen
        if (request.promotionIds() != null && !request.promotionIds().isEmpty()) {
            processPromotions(order, request.promotionIds());
        }

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
     * Procesar items de una orden (decrementar stock, etc)
     */
    private void processOrderItems(Order order, List<OrderItemRequestDTO> items) {
        items.forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());

            boolean allowOutOfStock = Boolean.TRUE.equals(itemReq.allowOutOfStock());
            int requestedQuantity = itemReq.cantidad();
            int currentStock = product.getStock();
            boolean hasStock = currentStock >= requestedQuantity;

            // Validar stock solo si NO se permite venta sin stock
            if (!allowOutOfStock && !hasStock) {
                throw new BusinessExeption("Stock insuficiente para: " + product.getNombre());
            }

            // LÓGICA DE SPLIT DE INVENTARIO
            // Si hay stock parcial y se permite venta sin stock, dividimos
            if (!hasStock && currentStock > 0) {
                // PARTE 1: Lo que sí hay en stock (se descuenta)
                OrderItem inStockItem = new OrderItem(product, currentStock);
                inStockItem.setOutOfStock(false);
                inStockItem.setCantidadDescontada(currentStock); // Registramos lo que descontamos
                inStockItem.setCantidadPendiente(0);

                product.decreaseStock(currentStock); // Stock queda en 0
                order.addItem(inStockItem);

                // PARTE 2: Lo que falta (pendiente de suministro)
                int pendingQuantity = requestedQuantity - currentStock;
                OrderItem outOfStockItem = new OrderItem(product, pendingQuantity);
                outOfStockItem.setOutOfStock(true);
                outOfStockItem.setCantidadDescontada(0); // NO DESCONTAMOS NADA
                outOfStockItem.setCantidadPendiente(pendingQuantity);

                // NO descontamos stock para esto (stock ya es 0)
                order.addItem(outOfStockItem);

                log.info("Producto {} dividido: {} en stock, {} pendiente",
                        product.getNombre(), currentStock, pendingQuantity);

            } else {
                // CASO NORMAL (Todo con stock O Todo sin stock)
                OrderItem item = new OrderItem(product, requestedQuantity);

                if (hasStock) {
                    // Todo hay stock
                    item.setOutOfStock(false);
                    item.setCantidadDescontada(requestedQuantity);
                    item.setCantidadPendiente(0);
                    product.decreaseStock(requestedQuantity);
                } else {
                    // Nada hay stock (stock es 0 o insuficiente y no se hizo split)
                    // Nota: Si llegamos aqui con !hasStock es porque currentStock es 0
                    // (si fuera > 0 hubiera entrado en el if de arriba)
                    item.setOutOfStock(true);
                    item.setCantidadDescontada(0);
                    item.setCantidadPendiente(requestedQuantity);
                    log.warn("Producto agregado totalmente sin stock: {}", product.getNombre());
                }

                order.addItem(item);
            }
        });
    }

    /**
     * Procesar promociones de una orden
     */
    private void processPromotions(Order order, List<UUID> promotionIds) {
        promotionIds.forEach(promotionId -> {
            // Obtener y validar promoción
            Promotion promotion = promotionService.findEntityById(promotionId);

            // Validar vigencia
            if (!promotion.isValid()) {
                throw new BusinessExeption("La promoción '" + promotion.getNombre() + "' no está vigente");
            }

            if (!Boolean.TRUE.equals(promotion.getActive())) {
                throw new BusinessExeption("La promoción '" + promotion.getNombre() + "' no está activa");
            }

            // Obtener producto principal
            Product mainProduct = promotion.getMainProduct();

            // Crear OrderItem para los productos comprados (buyQuantity)
            OrderItem buyItem = OrderItem.builder()
                    .product(mainProduct)
                    .cantidad(promotion.getBuyQuantity())
                    .precioUnitario(mainProduct.getPrecio()) // Precio unitario del producto (informativo)
                    .subTotal(promotion.getPackPrice() != null
                            ? promotion.getPackPrice() // Usar packPrice directamente como subtotal
                            : mainProduct.getPrecio().multiply(BigDecimal.valueOf(promotion.getBuyQuantity())))
                    .promotion(promotion)
                    .isPromotionItem(true)
                    .isFreeItem(false)
                    .build();

            // Validar y decrementar stock para productos comprados
            if (mainProduct.getStock() < promotion.getBuyQuantity()) {
                log.warn("Stock insuficiente para promoción '{}'. Disponible: {}, Requerido: {}",
                        promotion.getNombre(), mainProduct.getStock(), promotion.getBuyQuantity());
                buyItem.setOutOfStock(true);
            } else {
                mainProduct.decreaseStock(promotion.getBuyQuantity());
            }

            order.addItem(buyItem);

            // Manejar productos gratis/surtidos
            if (promotion.isAssortment()) {
                // Tipo ASSORTMENT: El admin debe seleccionar los productos surtidos
                // La orden queda pendiente de completar la promoción
                order.setEstado(OrdenStatus.PENDING_PROMOTION_COMPLETION);
                log.info(
                        "Orden {} marcada como PENDING_PROMOTION_COMPLETION para selección de surtidos (Tipo ASSORTMENT)",
                        order.getId());
            } else if (promotion.isFixed()) {
                // Tipo FIXED: Agregar items de regalo predefinidos
                if (promotion.getGiftItems() != null) {
                    for (org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem gift : promotion
                            .getGiftItems()) {
                        Product freeProduct = gift.getProduct();
                        Integer qty = gift.getQuantity();

                        OrderItem freeItem = OrderItem.builder()
                                .product(freeProduct)
                                .cantidad(qty)
                                .precioUnitario(BigDecimal.ZERO)
                                .subTotal(BigDecimal.ZERO)
                                .promotion(promotion)
                                .isPromotionItem(true)
                                .isFreeItem(true)
                                .build();

                        // Validar y decrementar stock para productos gratis
                        if (freeProduct.getStock() < qty) {
                            log.warn(
                                    "Stock insuficiente para producto gratis de promoción '{}'. Disponible: {}, Requerido: {}",
                                    promotion.getNombre(), freeProduct.getStock(), qty);
                            freeItem.setOutOfStock(true);
                        } else {
                            freeProduct.decreaseStock(qty);
                        }

                        order.addItem(freeItem);
                    }
                }
            } else {
                log.warn("Tipo de promoción desconocido o no manejado: {}", promotion.getType());
            }

            log.info("Promoción '{}' aplicada a orden", promotion.getNombre());
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
        // RESTAURAR STOCK de items anteriores
        order.getItems().forEach(item -> {
            Product product = item.getProduct();
            // CORRECCIÓN CRITICA: Solo devolvemos al stock lo que realmente se descontó
            // Si es un item antiguo sin este campo (null), usamos la lógica anterior pero
            // con cuidado
            int stockToRestore = 0;
            if (item.getCantidadDescontada() != null) {
                stockToRestore = item.getCantidadDescontada();
            } else {
                // Fallback para items viejos: si no estaba marcado como outOfStock, asumimos
                // que descontó todo
                if (!Boolean.TRUE.equals(item.getOutOfStock())) {
                    stockToRestore = item.getCantidad();
                }
            }

            if (stockToRestore > 0) {
                product.increaseStock(stockToRestore);
            }
        });

        // Limpiar items actuales
        order.clearItems();

        // AGREGAR NUEVOS ITEMS (con validación de stock y split)
        // Reutilizamos la lógica de split implementada en processOrderItems pero
        // adaptada o llamamos al metodo
        // Como processOrderItems recibe DTOs y aqui ya tenemos DTOs del request,
        // podemos reutilizar lógica similar

        request.items().forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());

            // En edición (Admin), SIEMPRE permitimos out of stock implícitamente si se
            // desea,
            // pero mantenemos coherencia de inventario

            int requestedQuantity = itemReq.cantidad();
            int currentStock = product.getStock();
            boolean hasStock = currentStock >= requestedQuantity;

            // LÓGICA DE SPLIT DE INVENTARIO (Idéntica a create)
            if (!hasStock && currentStock > 0) {
                // PARTE 1: Stock disponible
                OrderItem inStockItem = new OrderItem(product, currentStock);
                inStockItem.setOutOfStock(false);
                inStockItem.setCantidadDescontada(currentStock);
                inStockItem.setCantidadPendiente(0);

                product.decreaseStock(currentStock);
                order.addItem(inStockItem);

                // PARTE 2: Pendiente
                int pendingQuantity = requestedQuantity - currentStock;
                OrderItem outOfStockItem = new OrderItem(product, pendingQuantity);
                outOfStockItem.setOutOfStock(true);
                outOfStockItem.setCantidadDescontada(0);
                outOfStockItem.setCantidadPendiente(pendingQuantity);

                order.addItem(outOfStockItem);
                log.info("Producto {} dividido en edición: {} stock, {} pendiente",
                        product.getNombre(), currentStock, pendingQuantity);

            } else {
                // Todo o nada
                OrderItem item = new OrderItem(product, requestedQuantity);

                if (hasStock) {
                    item.setOutOfStock(false);
                    item.setCantidadDescontada(requestedQuantity);
                    item.setCantidadPendiente(0);
                    product.decreaseStock(requestedQuantity);
                } else {
                    item.setOutOfStock(true);
                    item.setCantidadDescontada(0);
                    item.setCantidadPendiente(requestedQuantity);
                    log.warn("Producto agregado sin stock en edición de orden {}: {}", orderId, product.getNombre());
                }
                order.addItem(item);
            }
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

            boolean hasStock = product.getStock() >= itemReq.cantidad();

            // Crear OrderItem con precio 0 (gratis/bonificado)
            OrderItem assortmentItem = OrderItem.builder()
                    .product(product)
                    .cantidad(itemReq.cantidad())
                    .precioUnitario(BigDecimal.ZERO) // GRATIS
                    .subTotal(BigDecimal.ZERO)
                    .promotion(promotion)
                    .isPromotionItem(true)
                    .isFreeItem(true)
                    .outOfStock(!hasStock) // Marcar si no hay stock
                    .cantidadDescontada(hasStock ? itemReq.cantidad() : 0)
                    .cantidadPendiente(hasStock ? 0 : itemReq.cantidad())
                    .build();

            order.addItem(assortmentItem);

            // Guardar explícitamente el item para evitar TransientObjectException
            ordenItemRepository.save(assortmentItem);

            // Decrementar stock si hay disponible
            if (hasStock) {
                product.decreaseStock(itemReq.cantidad());
            } else {
                log.warn("Stock insuficiente para producto surtido {}, se marcó como sin stock", product.getNombre());
            }
        });

        // Cambiar estado de orden a CONFIRMADO
        order.setEstado(OrdenStatus.CONFIRMADO);

        ordenRepository.save(order);

        log.info("Productos surtidos agregados exitosamente a orden {}", orderId);
        log.info("Productos surtidos agregados exitosamente a orden {}", orderId);
    }

    @Override
    @Transactional
    public void updateItemEta(UUID orderId, UUID itemId, LocalDate eta, String note) {
        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessExeption("Item no encontrado en la orden"));

        if (!Boolean.TRUE.equals(item.getOutOfStock())) {
            // Opcional: permitir poner ETA aunque tenga stock, pero el caso de uso es para
            // sin stock
            // Por ahora logueamos warning
            log.warn("Asignando ETA a producto que tiene stock: {}", item.getProduct().getNombre());
        }

        item.setEstimatedArrivalDate(eta);
        item.setEstimatedArrivalNote(note);

        // Guardar explícitamente el item
        ordenItemRepository.save(item);

        log.info("ETA actualizado para item {} en orden {}: {} ({})", itemId, orderId, eta, note);
    }
}
