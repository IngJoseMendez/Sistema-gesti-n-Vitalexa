package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.AddAssortmentItemRequest;
import org.example.sistema_gestion_vitalexa.dto.BonifiedItemRequestDTO;
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

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Usuario actual no encontrado"));

        User vendedor = currentUser;

        // Lógica "Admin vendediendo como otro usuario"
        if (request.sellerId() != null) {
            // Solo Admin u Owner pueden hacer esto
            if (currentUser.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.ADMIN ||
                    currentUser.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.OWNER) {

                vendedor = userRepository.findById(request.sellerId())
                        .orElseThrow(() -> new BusinessExeption("Vendedor especificado no encontrado"));

                log.info("Admin {} creando orden a nombre de vendedor {}", username, vendedor.getUsername());
            } else {
                log.warn("Usuario {} intentó vender como otro sin permisos", username);
                // No lanzamos excepción, simplemente ignoramos y usamos el usuario actual (o
                // lanzamos error segun prefieras)
                // Para ser estrictos:
                throw new BusinessExeption("No tiene permisos para crear órdenes a nombre de otro vendedor");
            }
        }

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
                            if (Boolean.TRUE.equals(item.isFreightItem())) {
                                return BigDecimal.ZERO;
                            }
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

        // Separar items en listas: Normales, S/R y Relacionados a Promo
        // IMPORTANTE: Los items de flete NO se deben procesar como items normales
        List<OrderItemRequestDTO> normalItems = new java.util.ArrayList<>();
        List<OrderItemRequestDTO> srItems = new java.util.ArrayList<>();
        List<OrderItemRequestDTO> promoItems = new java.util.ArrayList<>(); // Items que pertenecen a una promo surtida
        List<OrderItemRequestDTO> freightItems = new java.util.ArrayList<>(); // Items de flete personalizado

        ProductTag finalSrTag = srTag;
        if (request.items() != null) {
            request.items().forEach(itemReq -> {
                // Prioridad 0: Es item de flete? -> No procesar como item normal
                if (Boolean.TRUE.equals(itemReq.isFreightItem())) {
                    freightItems.add(itemReq);
                    return;
                }

                Product product = productService.findEntityById(itemReq.productId());

                // Prioridad 1: Pertenece a una promoción
                if (itemReq.relatedPromotionId() != null) {
                    promoItems.add(itemReq);
                    return;
                }

                // Prioridad 2: Es SR
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
        boolean hasPromo = !promotionIds.isEmpty() || !promoItems.isEmpty(); // Promo activa si hay IDs o items
                                                                             // relacionados

        int typesCount = (hasNormal ? 1 : 0) + (hasSR ? 1 : 0) + (hasPromo ? 1 : 0);

        if (typesCount <= 1) {
            // Caso simple: Crear una sola orden con lo que haya
            String noteSuffix = "";
            if (hasSR && !hasNormal && !hasPromo) {
                noteSuffix = " [S/R]";
            }
            return createSingleOrder(vendedor, client, request, username, noteSuffix);
        } else {
            // Caso múltiple: Crear órdenes separadas
            // Construir descripción de flete personalizado si hay items de flete
            String freightDesc = request.freightCustomText();
            if (!freightItems.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                if (freightDesc != null && !freightDesc.isBlank()) {
                    sb.append(freightDesc).append(" - ");
                }
                sb.append("Incluye: ");
                freightItems.forEach(item -> {
                    Product p = productService.findEntityById(item.productId());
                    sb.append(p.getNombre()).append(" x").append(item.cantidad()).append(", ");
                });
                // Remover última coma
                String desc = sb.toString();
                if (desc.endsWith(", ")) {
                    desc = desc.substring(0, desc.length() - 2);
                }
                freightDesc = desc;
            }

            return createMultipleOrders(vendedor, client, normalItems, srItems, promoItems, promotionIds,
                    request.notas(),
                    Boolean.TRUE.equals(request.includeFreight()),
                    Boolean.TRUE.equals(request.isFreightBonified()),
                    freightDesc,
                    request.freightQuantity(),
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
            List<OrderItemRequestDTO> promoItems,
            List<UUID> promotionIds,
            String notas,
            boolean includeFreight,
            boolean isFreightBonified,
            String freightCustomText,
            Integer freightQuantity,
            String username) {

        OrderResponse response = null;
        BigDecimal totalPurchase = BigDecimal.ZERO;
        StringBuilder logMsg = new StringBuilder("Orden dividida en: ");

        // 1. ORDEN STANDARD (Solo Items normales - SIN Promociones aquí)
        // Las promociones van en su propia orden
        if (!normalItems.isEmpty()) {
            Order standardOrder = new Order(vendedor, client);
            if (notas != null && !notas.isBlank()) {
                standardOrder.setNotas(notas + " [Standard]");
            }
            // Aplicar flete a la orden normal si corresponde y tiene permisos
            if (includeFreight) {
                if (vendedor.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.ADMIN ||
                        vendedor.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.OWNER) {
                    standardOrder.setIncludeFreight(true);

                    // Configuración de flete personalizado/bonificado
                    if (isFreightBonified) {
                        standardOrder.setIsFreightBonified(true);
                    }
                    if (freightCustomText != null) {
                        standardOrder.setFreightCustomText(freightCustomText);
                    }
                    if (freightQuantity != null) {
                        standardOrder.setFreightQuantity(freightQuantity);
                    }

                    includeFreight = false; // Ya se aplicó, no aplicar en las siguientes
                } else {
                    throw new BusinessExeption("Solo administradores pueden incluir flete.");
                }
            }

            processOrderItems(standardOrder, normalItems);

            Order saved = ordenRepository.save(standardOrder);

            notificationService.sendNewOrderNotification(saved.getId().toString(), vendedor.getUsername(),
                    client != null ? client.getNombre() : "Sin cliente");

            totalPurchase = totalPurchase.add(saved.getTotal());
            response = orderMapper.toResponse(saved); // Prioridad 1 para response
            logMsg.append("Standard (").append(saved.getId()).append(") ");
        }

        // 2. ORDEN S/R
        // Solo items que pertenecen a productos marcados con etiqueta S/R
        if (!srItems.isEmpty()) {
            Order srOrder = new Order(vendedor, client);
            // Si solo hay S/R y Promos, pero no normal, y tenemos notas, aseguramos el tag S/R
            String suffix = " [S/R]";
            srOrder.setNotas((notas != null ? notas : "") + suffix);

            // Procesar items S/R (también pueden dividirse en con/sin stock)
            processOrderItems(srOrder, srItems);

            if (!srOrder.getItems().isEmpty()) {
                Order saved = ordenRepository.save(srOrder);

                notificationService.sendNewOrderNotification(saved.getId().toString(), vendedor.getUsername(),
                        client != null ? client.getNombre() : "Sin cliente");

                totalPurchase = totalPurchase.add(saved.getTotal());
                if (response == null)
                    response = orderMapper.toResponse(saved); // Prioridad 2
                logMsg.append("S/R (").append(saved.getId()).append(") ");
            }
        }

        // 3. ORDEN PROMOCIONES
        // Items que pertenecen específicamente a una promoción surtida, SEPARADOS de las órdenes normales
        if (!promoItems.isEmpty() || !promotionIds.isEmpty()) {
            Order promoOrder = new Order(vendedor, client);
            String suffix = " [Promoción]";
            promoOrder.setNotas((notas != null ? notas : "") + suffix);

            // Aplicar flete a la orden de promoción si corresponde
            if (includeFreight) {
                if (vendedor.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.ADMIN ||
                        vendedor.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.OWNER) {
                    promoOrder.setIncludeFreight(true);

                    // Configuración de flete personalizado/bonificado
                    if (isFreightBonified) {
                        promoOrder.setIsFreightBonified(true);
                    }
                    if (freightCustomText != null) {
                        promoOrder.setFreightCustomText(freightCustomText);
                    }
                    if (freightQuantity != null) {
                        promoOrder.setFreightQuantity(freightQuantity);
                    }

                    includeFreight = false; // Ya se aplicó, no aplicar en las siguientes
                } else {
                    throw new BusinessExeption("Solo administradores pueden incluir flete.");
                }
            }

            // Agregar items que pertenecen específicamente a la promo
            if (!promoItems.isEmpty()) {
                processOrderItems(promoOrder, promoItems);
            }

            // Validar que la cantidad de items en ESTA orden cumpla con la promo
            // En este flujo, los items de la promo deben estar DENTRO de la promoOrder
            int promoItemsCount = promoItems.stream().mapToInt(OrderItemRequestDTO::cantidad).sum();

            if (!promotionIds.isEmpty()) {
                processPromotions(promoOrder, promotionIds, promoItemsCount);
            }

            // Solo guardar si tiene items (evita crear órdenes vacías de promoción)
            if (!promoOrder.getItems().isEmpty()) {
                Order saved = ordenRepository.save(promoOrder);

                notificationService.sendNewOrderNotification(saved.getId().toString(), vendedor.getUsername(),
                        client != null ? client.getNombre() : "Sin cliente");

                totalPurchase = totalPurchase.add(saved.getTotal());
                if (response == null)
                    response = orderMapper.toResponse(saved); // Prioridad 3
                logMsg.append("Promo (").append(saved.getId()).append(") ");
            } else {
                log.warn("Orden de promoción vacía descartada (sin items válidos)");
            }
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
    private OrderResponse createSingleOrder(User vendedor, Client client, OrderRequestDto request, String username,
            String noteSuffix) {
        Order order = new Order(vendedor, client);

        String finalNotes = request.notas() != null ? request.notas() : "";
        if (noteSuffix != null && !noteSuffix.isEmpty()) {
            finalNotes += noteSuffix;
        }

        if (!finalNotes.isBlank()) {
            order.setNotas(finalNotes);
        }

        if (Boolean.TRUE.equals(request.includeFreight())) {
            // Validar que solo ADMIN u OWNER puedan incluir flete
            if (vendedor.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.ADMIN ||
                    vendedor.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.OWNER) {
                order.setIncludeFreight(true);

                // Configuración de flete personalizado/bonificado
                if (Boolean.TRUE.equals(request.isFreightBonified())) {
                    order.setIsFreightBonified(true);
                }

                // Construir descripción de flete personalizado si hay items de flete
                String freightDesc = request.freightCustomText();
                List<OrderItemRequestDTO> freightItems = new java.util.ArrayList<>();
                if (request.items() != null) {
                    freightItems = request.items().stream()
                        .filter(item -> Boolean.TRUE.equals(item.isFreightItem()))
                        .toList();
                }

                if (!freightItems.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    if (freightDesc != null && !freightDesc.isBlank()) {
                        sb.append(freightDesc).append(" - ");
                    }
                    sb.append("Incluye: ");
                    freightItems.forEach(item -> {
                        Product p = productService.findEntityById(item.productId());
                        sb.append(p.getNombre()).append(" x").append(item.cantidad()).append(", ");
                    });
                    // Remover última coma
                    String desc = sb.toString();
                    if (desc.endsWith(", ")) {
                        desc = desc.substring(0, desc.length() - 2);
                    }
                    freightDesc = desc;
                }

                order.setFreightCustomText(freightDesc);
                if (request.freightQuantity() != null) {
                    order.setFreightQuantity(request.freightQuantity());
                }

            } else {
                throw new BusinessExeption("Solo administradores pueden incluir flete.");
            }
        }

        // Procesar items regulares si existen (EXCLUYENDO items de flete)
        if (request.items() != null && !request.items().isEmpty()) {
            List<OrderItemRequestDTO> itemsToProcess = request.items().stream()
                .filter(item -> !Boolean.TRUE.equals(item.isFreightItem()))
                .toList();

            if (!itemsToProcess.isEmpty()) {
                processOrderItems(order, itemsToProcess);
            }
        }

        // Procesar promociones si existen
        if (request.promotionIds() != null && !request.promotionIds().isEmpty()) {
            // En una orden simple, los items ya están en la orden
            int totalNormalItemsCount = order.getItems().stream()
                    .filter(i -> !Boolean.TRUE.equals(i.getIsPromotionItem()))
                    .mapToInt(OrderItem::getCantidad)
                    .sum();
            processPromotions(order, request.promotionIds(), totalNormalItemsCount);
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
            // ✅ IMPORTANTE: NO procesar items de flete
            if (Boolean.TRUE.equals(itemReq.isFreightItem())) {
                log.debug("Item de flete ignorado en processOrderItems (no se agrega como producto): {} x{}",
                    itemReq.productId(), itemReq.cantidad());
                return;
            }

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
     * 
     * @param contextTotalNormalItems Cantidad total de items normales en la
     *                                transacción (para validar surtidos globales)
     */
    private void processPromotions(Order order, List<UUID> promotionIds, int contextTotalNormalItems) {
        log.info("Procesando promociones. IDs recibidos del request: {}", promotionIds);
        promotionIds.forEach(promotionId -> {
            log.info("Buscando promoción con ID: {}", promotionId);
            // Obtener y validar promoción
            Promotion promotion = promotionService.findEntityById(promotionId);

            // Validar vigencia
            if (!promotion.isValid()) {
                throw new BusinessExeption("La promoción '" + promotion.getNombre() + "' no está vigente");
            }

            if (!Boolean.TRUE.equals(promotion.getActive())) {
                throw new BusinessExeption("La promoción '" + promotion.getNombre() + "' no está activa");
            }

            // ==========================================
            // Lógica Diferenciada: SURTIDA vs PREDEFINIDA
            // ==========================================

            if (promotion.isAssortment()) {
                // =========================
                // CASO 1: PROMOCIÓN SURTIDA (Mix & Match)
                // =========================
                // ITEMS COMPRADOS:
                // No agregamos una línea "Padre". Confiamos en que los items individuales
                // ya fueron agregados a la orden (en la lista promoItems) y suman la cantidad
                // requerida.

                // Validamos que la cantidad total cumpla el requisito
                if (contextTotalNormalItems < promotion.getBuyQuantity()) {
                    throw new BusinessExeption("Para aplicar la promoción '" + promotion.getNombre() +
                            "' debe agregar al menos " + promotion.getBuyQuantity() + " productos a la orden.");
                }

                // ITEMS DE REGALO (Bonificados):
                // Agregamos los items de regalo definidos (ej: "15 Surtidos Genericos") como
                // placeholders
                if (promotion.getGiftItems() != null) {
                    for (org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem gift : promotion
                            .getGiftItems()) {
                        Product freeProduct = gift.getProduct();
                        Integer qty = gift.getQuantity();

                        // Crear item con precio 0 explicitamente
                        OrderItem placeholderItem = OrderItem.builder()
                                .product(freeProduct)
                                .cantidad(qty)
                                .precioUnitario(BigDecimal.ZERO) // GRATIS
                                .subTotal(BigDecimal.ZERO)
                                .promotion(promotion)
                                .isPromotionItem(true)
                                .isFreeItem(true)
                                .build();

                        // Validar stock del regalo (si aplica)
                        if (freeProduct.getStock() < qty) {
                            log.warn("Stock insuficiente para regalo '{}'. Disponible: {}, Requerido: {}",
                                    freeProduct.getNombre(), freeProduct.getStock(), qty);
                            placeholderItem.setOutOfStock(true);
                        } else {
                            freeProduct.decreaseStock(qty);
                        }

                        order.addItem(placeholderItem);
                    }
                }

            } else {
                // =========================
                // CASO 2: PROMOCIÓN PREDEFINIDA / FIJA
                // =========================
                Product mainProduct = promotion.getMainProduct();

                if (mainProduct != null) {
                    // Agregar el producto principal (Pack)
                    OrderItem buyItem = OrderItem.builder()
                            .product(mainProduct)
                            .cantidad(promotion.getBuyQuantity())
                            .precioUnitario(mainProduct.getPrecio())
                            .subTotal(promotion.getPackPrice() != null
                                    ? promotion.getPackPrice()
                                    : mainProduct.getPrecio().multiply(BigDecimal.valueOf(promotion.getBuyQuantity())))
                            .promotion(promotion)
                            .isPromotionItem(true)
                            .isFreeItem(false)
                            .build();

                    if (mainProduct.getStock() < promotion.getBuyQuantity()) {
                        log.warn("Stock insuficiente para promoción '{}'. Disponible: {}, Requerido: {}",
                                promotion.getNombre(), mainProduct.getStock(), promotion.getBuyQuantity());
                        buyItem.setOutOfStock(true);
                    } else {
                        mainProduct.decreaseStock(promotion.getBuyQuantity());
                    }

                    order.addItem(buyItem);
                }

                // Agregar items de regalo fijos
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

                        if (freeProduct.getStock() < qty) {
                            log.warn("Stock insuficiente para regalo fijo '{}'. Disponible: {}, Requerido: {}",
                                    freeProduct.getNombre(), freeProduct.getStock(), qty);
                            freeItem.setOutOfStock(true);
                        } else {
                            freeProduct.decreaseStock(qty);
                        }

                        order.addItem(freeItem);
                    }
                }
            }

            log.info("Promoción '{}' aplicada correctamente.", promotion.getNombre());
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

        // VALIDAR QUE HAYA ITEMS O PROMOCIONES
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        boolean hasPromotions = request.promotionIds() != null && !request.promotionIds().isEmpty();


        if (!hasItems && !hasPromotions) {
            throw new BusinessExeption("La orden debe tener al menos un producto o una promoción");
        }

        // RESTAURAR STOCK de items anteriores (solo los que no son items de promoción)
        order.getItems().forEach(item -> {
            Product product = item.getProduct();
            // No restaurar stock de items regalados (isFreeItem=true) - estos son de promo
            if (Boolean.TRUE.equals(item.getIsFreeItem())) {
                log.info("Item de regalo no restaura stock: {}", product.getNombre());
                return;
            }

            // No restaurar stock de items de promoción
            if (Boolean.TRUE.equals(item.getIsPromotionItem())) {
                log.info("Item de promoción no restaura stock en edición: {}", product.getNombre());
                return;
            }

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

        // Limpiar SOLO items no promocionados (preservar items de promo)
        List<OrderItem> promotionItems = new java.util.ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (Boolean.TRUE.equals(item.getIsPromotionItem())) {
                promotionItems.add(item);
            }
        }
        order.clearItems();

        // Re-agregar items de promoción para preservar precios
        for (OrderItem promoItem : promotionItems) {
            order.addItem(promoItem);
        }

        // DETECTAR TIPO DE ORDEN (por suffix en notas)
        String currentNotes = order.getNotas() != null ? order.getNotas() : "";
        boolean isPromoOrder = currentNotes.contains("[Promoción]");

        // AGREGAR NUEVOS ITEMS (con validación de stock y split)
        // IMPORTANTE:
        // - NO procesar items de flete como items normales
        // - Si es orden Promo: NO agregar items normales
        // - Si es orden Normal/S/R: Agregar todos los items excepto flete
        if (hasItems) {
            // Filtrar items de flete
            List<OrderItemRequestDTO> freightItemsReq = new java.util.ArrayList<>();
            List<OrderItemRequestDTO> normalItemsReq = new java.util.ArrayList<>();

            request.items().forEach(itemReq -> {
                if (Boolean.TRUE.equals(itemReq.isFreightItem())) {
                    freightItemsReq.add(itemReq);
                } else {
                    normalItemsReq.add(itemReq);
                }
            });

            // Procesar items normales (no flete)
            normalItemsReq.forEach(itemReq -> {
                // ❌ BLOQUEAR items normales en orden de Promo
                if (isPromoOrder) {
                    log.debug("Item normal ignorado en edición de orden promo: {}", itemReq.productId());
                    return;
                }

                Product product = productService.findEntityById(itemReq.productId());

                // En edición (Admin), SIEMPRE permitimos out of stock implícitamente si se
                // desea, pero mantenemos coherencia de inventario

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

            // Actualizar descripción de flete si hay items de flete
            if (!freightItemsReq.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                String existingFreightText = order.getFreightCustomText();
                if (existingFreightText != null && !existingFreightText.isBlank()) {
                    // Extraer solo la parte descriptiva sin los items previos
                    String[] parts = existingFreightText.split(" - Incluye:");
                    if (parts.length > 0) {
                        sb.append(parts[0]).append(" - ");
                    }
                }
                sb.append("Incluye: ");
                freightItemsReq.forEach(item -> {
                    Product p = productService.findEntityById(item.productId());
                    sb.append(p.getNombre()).append(" x").append(item.cantidad()).append(", ");
                });
                // Remover última coma
                String desc = sb.toString();
                if (desc.endsWith(", ")) {
                    desc = desc.substring(0, desc.length() - 2);
                }
                order.setFreightCustomText(desc);
            }
        } else if (isPromoOrder && hasItems) {
            log.debug("Edición de orden de promo: Se ignoran items normales (solo se preservan regalos y flete)");
        }

        // PROCESAR BONIFICADOS (si la orden NO es de promo)
        if (!isPromoOrder && request.bonifiedItems() != null && !request.bonifiedItems().isEmpty()) {
            processBonifiedItems(order, request.bonifiedItems());
        }

        // PROCESAR PROMOCIONES (CRÍTICO: Restaurar promociones al editar)
        if (hasPromotions) {
            int totalNormalItemsCount = order.getItems().stream()
                    .filter(i -> !Boolean.TRUE.equals(i.getIsPromotionItem()))
                    .mapToInt(OrderItem::getCantidad)
                    .sum();
            processPromotions(order, request.promotionIds(), totalNormalItemsCount);
            log.info("Promociones restauradas en edición de orden {}: {}", orderId, request.promotionIds());
        }

        // Actualizar notas - PRESERVAR SUFIJOS DE TIPO DE ORDEN
        String newNotes = request.notas() != null ? request.notas() : "";

        // Detectar y preservar sufijos de tipo de orden
        String suffix = "";
        if (currentNotes.contains("[Promoción]")) {
            suffix = " [Promoción]";
        } else if (currentNotes.contains("[S/R]")) {
            suffix = " [S/R]";
        } else if (currentNotes.contains("[Standard]")) {
            suffix = " [Standard]";
        }

        // Si la orden tiene promociones, asegurar que tiene el suffix [Promoción]
        if (hasPromotions && !suffix.contains("[Promoción]")) {
            suffix = " [Promoción]";
        }

        order.setNotas(newNotes + suffix);

        // Actualizar flete - PRESERVAR ESTADO DE PROMOCIÓN
        if (Boolean.TRUE.equals(request.includeFreight())) {
            order.setIncludeFreight(true);
            order.setIsFreightBonified(Boolean.TRUE.equals(request.isFreightBonified()));
            order.setFreightCustomText(request.freightCustomText());
            order.setFreightQuantity(request.freightQuantity() != null ? request.freightQuantity() : 1);
        } else {
            order.setIncludeFreight(false);
            order.setIsFreightBonified(false);
            order.setFreightCustomText(null);
            order.setFreightQuantity(1);
        }

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

    @Override
    @Transactional
    public void annulOrder(UUID orderId, String reason) {
        log.info("Anulando orden: {} por motivo: {}", orderId, reason);
        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        if (order.getEstado() == OrdenStatus.ANULADA) {
            throw new BusinessExeption("La orden ya está anulada");
        }

        // Restaurar Stock
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Product product = item.getProduct();
                // Ignorar sistema product y ocultos
                if (!"SURTIDO PROMOCIONAL".equals(product.getNombre()) && !product.isHidden()) {
                    product.increaseStock(item.getCantidad());
                    log.info("Stock restaurado para '{}': +{}", product.getNombre(), item.getCantidad());
                }
            }
        }

        order.setEstado(OrdenStatus.ANULADA);
        order.setCancellationReason(reason);
        ordenRepository.save(order);
    }

    /**
     * Procesar productos bonificados (regalos) de una orden
     * Los bonificados siempre tienen precio 0 y pueden estar sin stock
     */
    private void processBonifiedItems(Order order, List<BonifiedItemRequestDTO> bonifiedItems) {
        if (bonifiedItems == null || bonifiedItems.isEmpty()) {
            return;
        }

        bonifiedItems.forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());
            int requestedQuantity = itemReq.cantidad();
            int currentStock = product.getStock();
            boolean hasStock = currentStock >= requestedQuantity;

            // Para bonificados: permitimos out of stock sin restricción
            // Dividir en stock/sin stock si hay stock parcial
            if (!hasStock && currentStock > 0) {
                // PARTE 1: Lo que sí hay en stock
                OrderItem inStockItem = new OrderItem(product, currentStock);
                inStockItem.setIsBonified(true);
                inStockItem.setPrecioUnitario(BigDecimal.ZERO);
                inStockItem.setSubTotal(BigDecimal.ZERO);
                inStockItem.setOutOfStock(false);
                inStockItem.setCantidadDescontada(currentStock);
                inStockItem.setCantidadPendiente(0);

                product.decreaseStock(currentStock);
                order.addItem(inStockItem);

                // PARTE 2: Lo que falta (pendiente)
                int pendingQuantity = requestedQuantity - currentStock;
                OrderItem outOfStockItem = new OrderItem(product, pendingQuantity);
                outOfStockItem.setIsBonified(true);
                outOfStockItem.setPrecioUnitario(BigDecimal.ZERO);
                outOfStockItem.setSubTotal(BigDecimal.ZERO);
                outOfStockItem.setOutOfStock(true);
                outOfStockItem.setCantidadDescontada(0);
                outOfStockItem.setCantidadPendiente(pendingQuantity);

                order.addItem(outOfStockItem);
                log.info("Producto bonificado {} dividido: {} en stock, {} pendiente",
                        product.getNombre(), currentStock, pendingQuantity);

            } else {
                // Todo con stock o todo sin stock
                OrderItem item = new OrderItem(product, requestedQuantity);
                item.setIsBonified(true);
                item.setPrecioUnitario(BigDecimal.ZERO);
                item.setSubTotal(BigDecimal.ZERO);

                if (hasStock) {
                    item.setOutOfStock(false);
                    item.setCantidadDescontada(requestedQuantity);
                    item.setCantidadPendiente(0);
                    product.decreaseStock(requestedQuantity);
                } else {
                    item.setOutOfStock(true);
                    item.setCantidadDescontada(0);
                    item.setCantidadPendiente(requestedQuantity);
                    log.warn("Producto bonificado agregado sin stock: {}", product.getNombre());
                }

                order.addItem(item);
            }
        });
    }
}
