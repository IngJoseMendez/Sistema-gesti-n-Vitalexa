package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.AddAssortmentItemRequest;
import org.example.sistema_gestion_vitalexa.dto.BonifiedItemRequestDTO;
import org.example.sistema_gestion_vitalexa.dto.CreateHistoricalInvoiceRequest;
import org.example.sistema_gestion_vitalexa.dto.OrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.dto.OrderItemRequestDTO;
import org.example.sistema_gestion_vitalexa.entity.*;
import org.example.sistema_gestion_vitalexa.service.SpecialProductService;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.OrderMapper;
import org.example.sistema_gestion_vitalexa.repository.OrdenItemRepository;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.*;
import org.example.sistema_gestion_vitalexa.util.UserUnificationUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
    private final org.example.sistema_gestion_vitalexa.repository.PaymentRepository paymentRepository;
    private final org.example.sistema_gestion_vitalexa.repository.ClientRepository clientRepository;
    private final SpecialProductService specialProductService;
    private final InventoryMovementService movementService;
    private final SpecialPromotionService specialPromotionService;
    private final org.example.sistema_gestion_vitalexa.repository.SpecialPromotionRepository specialPromotionRepository;

    // =========================
    // CREATE ORDER (VENDEDOR)
    // =========================
    // CREATE ORDER (VENDEDOR)
    // =========================
    @Override
    public OrderResponse createOrder(OrderRequestDto request, String username) {
        // Validar que haya al menos items O promociones O bonificados
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        boolean hasPromotions = request.promotionIds() != null && !request.promotionIds().isEmpty();
        boolean hasBonifiedItems = request.bonifiedItems() != null && !request.bonifiedItems().isEmpty();

        if (!hasItems && !hasPromotions && !hasBonifiedItems) {
            throw new BusinessExeption("La venta debe tener al menos un producto, una promoción o productos bonificados");
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

                            // Si es Special Product
                            if (item.specialProductId() != null) {
                                try {
                                    SpecialProduct sp = specialProductService.findEntityById(item.specialProductId());
                                    return sp.getPrecio().multiply(BigDecimal.valueOf(item.cantidad()));
                                } catch (Exception e) {
                                    return BigDecimal.ZERO;
                                }
                            }

                            // Si es Regular Product
                            if (item.productId() != null) {
                                Product p = productService.findEntityById(item.productId());
                                return p.getPrecio().multiply(BigDecimal.valueOf(item.cantidad()));
                            }

                            // Si no tiene ningun ID (Ghost Item)
                            return BigDecimal.ZERO;
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

                // Prioridad 1: Pertenece a una promoción
                if (itemReq.relatedPromotionId() != null) {
                    promoItems.add(itemReq);
                    return;
                }

                // Prioridad 2: Es SR (Verificar si es producto regular o especial)
                boolean isSRProduct = false;

                if (itemReq.specialProductId() != null) {
                    SpecialProduct sp;
                    try {
                        sp = specialProductService.findEntityById(itemReq.specialProductId());
                    } catch (RuntimeException e) {
                        // Diagnóstico: Verificar si el ID existe como producto regular
                        try {
                            Product p = productService.findEntityById(itemReq.specialProductId());
                            throw new RuntimeException("ERROR: El ID " + itemReq.specialProductId() +
                                    " pertenece al producto regular '" + p.getNombre() +
                                    "', pero se envió como producto especial. POR FAVOR RECARGA LA PÁGINA.");
                        } catch (Exception ex) {
                            // No es producto regular tampoco, relanzar original
                        }
                        throw e;
                    }

                    isSRProduct = finalSrTag != null && sp.getTag() != null
                            && sp.getTag().getId().equals(finalSrTag.getId());
                } else {
                    if (itemReq.productId() == null) {
                        log.warn("Item ignorado: se interpretó como producto regular pero productId es null. Item: {}",
                                itemReq);
                        return;
                    }
                    Product product = productService.findEntityById(itemReq.productId());
                    isSRProduct = finalSrTag != null && product.getTag() != null
                            && product.getTag().getId().equals(finalSrTag.getId());
                }

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
            // Construir descripción de flete personalizado (solo el texto base)
            String freightDesc = request.freightCustomText();

            return createMultipleOrders(vendedor, client, normalItems, srItems, promoItems, promotionIds,
                    request.notas(),
                    Boolean.TRUE.equals(request.includeFreight()),
                    Boolean.TRUE.equals(request.isFreightBonified()),
                    freightDesc,
                    request.freightQuantity(),
                    freightItems, // Pasar lista de items de flete
                    request.bonifiedItems(), // Pasar lista de bonificados
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
            List<OrderItemRequestDTO> freightItems,
            List<BonifiedItemRequestDTO> bonifiedItems,
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

            // Procesar items de flete si existen y se aplicó flete a esta orden
            if (Boolean.TRUE.equals(standardOrder.getIncludeFreight()) && freightItems != null
                    && !freightItems.isEmpty()) {
                processFreightItems(standardOrder, freightItems);
            }

            processOrderItems(standardOrder, normalItems);

            // Procesar bonificados si existen
            if (bonifiedItems != null && !bonifiedItems.isEmpty()) {
                processBonifiedItems(standardOrder, bonifiedItems);
            }

            Order saved = ordenRepository.save(standardOrder);

            notificationService.sendNewOrderNotification(saved.getId().toString(), vendedor.getUsername(),
                    client != null ? client.getNombre() : "Sin cliente");

            totalPurchase = totalPurchase.add(saved.getTotal());
            response = orderMapper.toResponse(saved); // Prioridad 1 para response
            logMsg.append("Standard (").append(saved.getId()).append(") ");

            // Intentar pagar con saldo a favor
            if (client != null)
                processAutomaticPayment(saved, client);
        }

        // 2. ORDEN S/R
        // Solo items que pertenecen a productos marcados con etiqueta S/R
        if (!srItems.isEmpty()) {
            Order srOrder = new Order(vendedor, client);
            // Si solo hay S/R y Promos, pero no normal, y tenemos notas, aseguramos el tag
            // S/R
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

                // Intentar pagar con saldo a favor
                if (client != null)
                    processAutomaticPayment(saved, client);
            }
        }

        // 3. ORDEN PROMOCIONES
        // Items que pertenecen específicamente a una promoción surtida, SEPARADOS de
        // las órdenes normales
        if (!promoItems.isEmpty() || !promotionIds.isEmpty()) {
            Order promoOrder = new Order(vendedor, client);
            String suffix = " [Promoción]";
            promoOrder.setNotas((notas != null ? notas : "") + suffix);

            // ⛔ Flete NO se aplica a órdenes de promoción
            // El flete se aplica únicamente a la orden Standard o S/R
            promoOrder.setIncludeFreight(false);

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

                // Intentar pagar con saldo a favor
                if (client != null)
                    processAutomaticPayment(saved, client);
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
     * Procesa el pago automático con saldo a favor si el cliente tiene saldo
     * disponible
     */
    private void processAutomaticPayment(Order order, Client client) {
        if (client == null || client.getBalanceFavor() == null ||
                client.getBalanceFavor().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal orderTotal = order.getDiscountedTotal() != null ? order.getDiscountedTotal() : order.getTotal();
        BigDecimal currentBalance = client.getBalanceFavor();

        // Calcular cuánto falta por pagar (aunque sea nueva, por robustez)
        BigDecimal alreadyPaid = paymentRepository.sumPaymentsByOrderId(order.getId());
        if (alreadyPaid == null)
            alreadyPaid = BigDecimal.ZERO;

        BigDecimal pending = orderTotal.subtract(alreadyPaid);

        if (pending.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Determinar cuánto vamos a pagar
        BigDecimal amountToPay = pending.min(currentBalance);

        if (amountToPay.compareTo(BigDecimal.ZERO) > 0) {
            // Crear el pago
            Payment payment = Payment.builder()
                    .order(order)
                    .amount(amountToPay)
                    .paymentDate(java.time.LocalDateTime.now())
                    .registeredBy(order.getVendedor()) // Registrado por el mismo vendedor que crea la orden
                    .notes("Pago automático con saldo a favor")
                    .withinDeadline(true) // Asumimos a tiempo porque es instantáneo
                    .build();
            paymentRepository.save(payment);

            // Actualizar saldo del cliente
            client.setBalanceFavor(currentBalance.subtract(amountToPay));
            // No guardamos el cliente aquí porque se guarda por cascada o referencia en la
            // transacción,
            // pero para estar seguros y evitar problemas de estado detachado:
            clientRepository.save(client);

            // Actualizar estado de pago de la orden
            if (amountToPay.compareTo(pending) >= 0) { // Si pagamos todo lo pendiente
                order.setPaymentStatus(org.example.sistema_gestion_vitalexa.enums.PaymentStatus.PAID);
            } else {
                order.setPaymentStatus(org.example.sistema_gestion_vitalexa.enums.PaymentStatus.PARTIAL);
            }
            ordenRepository.save(order);

            log.info("Pago automático de ${} aplicado a orden {} usando saldo a favor del cliente {}",
                    amountToPay, order.getId(), client.getNombre());
        }
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

            } else {
                throw new BusinessExeption("Solo administradores pueden incluir flete.");
            }
        }

        // Procesar items de flete si existen (como entidades)
        if (Boolean.TRUE.equals(order.getIncludeFreight())) {
            List<OrderItemRequestDTO> freightItemsToProcess = new java.util.ArrayList<>();
            if (request.items() != null) {
                freightItemsToProcess = request.items().stream()
                        .filter(item -> Boolean.TRUE.equals(item.isFreightItem()))
                        .toList();
            }
            if (!freightItemsToProcess.isEmpty()) {
                processFreightItems(order, freightItemsToProcess);
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

        // Procesar productos bonificados si existen
        if (request.bonifiedItems() != null && !request.bonifiedItems().isEmpty()) {
            processBonifiedItems(order, request.bonifiedItems());
        }

        Order savedOrder = ordenRepository.save(order);

        notificationService.sendNewOrderNotification(
                savedOrder.getId().toString(),
                vendedor.getUsername(),
                client != null ? client.getNombre() : "Sin cliente");

        if (client != null) {
            client.registerPurchase(savedOrder.getTotal());

            // Intentar pagar con saldo a favor
            processAutomaticPayment(savedOrder, client);
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

            boolean isSpecial = itemReq.specialProductId() != null;
            SpecialProduct specialProduct = null;

            if (isSpecial) {
                specialProduct = specialProductService.findEntityById(itemReq.specialProductId());
            }

            Product product = null;
            if (itemReq.productId() != null) {
                product = productService.findEntityById(itemReq.productId());
            } else if (specialProduct != null && specialProduct.getParentProduct() != null) {
                product = specialProduct.getParentProduct();
            } else {
                if (specialProduct != null) {
                    // Solución temporal para Standalone: Debemos asignar un "Producto Dummy" o
                    // permitir null en DB.
                    // Por ahora asumimos que no hay standalone. Si hubiese, lanzamos error claro.
                    throw new BusinessExeption(
                            "Error interno: Producto especial sin producto base no soportado en orden.");
                } else {
                    log.warn("Item ignorado en processOrderItems por falta de ID: {}", itemReq);
                    return;
                }
            }

            // Datos efectivos (Stock y Nombre y Precio)
            int currentStock;
            String productName;
            BigDecimal effectivePrice;

            if (isSpecial && specialProduct != null) { // null check redundante pero seguro para IDE
                currentStock = specialProduct.getEffectiveStock();
                productName = specialProduct.getNombre();
                effectivePrice = specialProduct.getPrecio();
            } else {
                currentStock = product.getStock();
                productName = product.getNombre();
                effectivePrice = product.getPrecio();
            }

            boolean allowOutOfStock = Boolean.TRUE.equals(itemReq.allowOutOfStock());
            int requestedQuantity = itemReq.cantidad();
            boolean hasStock = currentStock >= requestedQuantity;

            // Validar stock solo si NO se permite venta sin stock
            // CAMBIO: Ahora SIEMPRE permitimos venta sin stock (generará stock negativo)
            // if (!allowOutOfStock && !hasStock) {
            // throw new BusinessExeption("Stock insuficiente para: " + productName);
            // }

            // LÓGICA SIMPLIFICADA: SIEMPRE VENDER, STOCK PUEDE SER NEGATIVO

            // CASO UNICO: Todo se vende "de una"
            OrderItem item = new OrderItem(product, requestedQuantity);

            // Ajustar precio y vincular specialProduct
            if (specialProduct != null) {
                item.setPrecioUnitario(effectivePrice);
                item.setSubTotal(effectivePrice.multiply(BigDecimal.valueOf(requestedQuantity)));
                item.setSpecialProduct(specialProduct);
                specialProduct.decreaseStock(requestedQuantity);
            } else {
                product.decreaseStock(requestedQuantity);
            }

            // Marcamos flags
            if (hasStock) {
                item.setOutOfStock(false);
            } else {
                // Aunque no haya stock, permitimos la venta y marcamos flag informativo
                item.setOutOfStock(true);
                log.info("Producto {} vendido sin stock suficiente via 'Negative Stock'. Stock actual (pre-venta): {}",
                        productName, currentStock);
            }

            // SIEMPRE descontamos todo para el sistema (porque ya bajamos el stock
            // físico/lógico)
            item.setCantidadDescontada(requestedQuantity);
            item.setCantidadPendiente(0);

            order.addItem(item);

            // LOG INVENTORY MOVEMENT
            try {
                // Solo logueamos si el producto tiene stock gestionable
                if (product.getStock() != null) {
                    movementService.logMovement(
                            product,
                            org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType.SALE,
                            requestedQuantity,
                            currentStock,
                            product.getStock(),
                            "Venta Orden",
                            order.getVendedor() != null ? order.getVendedor().getUsername() : "System");
                }
            } catch (Exception e) {
                log.error("Error logging inventory movement: {}", e.getMessage());
            }
        });
    }

    /**
     * Procesar items DE FLETE (stock igual que items normales, precio 0, flag
     * activado)
     */
    /**
     * Procesar items de flete (productos enviados como flete personalizado)
     * ✅ Los items de flete siempre tienen precio 0 y permiten stock negativo
     */
    private void processFreightItems(Order order, List<OrderItemRequestDTO> items) {
        items.forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());
            int requestedQuantity = itemReq.cantidad();

            // Crear item de flete
            OrderItem item = new OrderItem(product, requestedQuantity);
            item.setPrecioUnitario(BigDecimal.ZERO);
            item.setSubTotal(BigDecimal.ZERO);
            item.setIsFreightItem(true);

            // ✅ IGUAL QUE PRODUCTOS NORMALES: Siempre descontar la cantidad completa (permite stock negativo)
            item.setCantidadDescontada(requestedQuantity);
            item.setCantidadPendiente(0);
            item.setOutOfStock(false);

            // Descontar stock (permite negativo)
            product.decreaseStock(requestedQuantity);

            order.addItem(item);

            log.info("✅ Item de flete {} agregado: cantidad={}, stock resultante={}",
                    product.getNombre(), requestedQuantity, product.getStock());
        });
    }

    /**
     * Procesar promociones de una orden
     * ✅ ACTUALIZADO: Genera IDs únicos para cada instancia de promoción
     *
     * @param contextTotalNormalItems Cantidad total de items normales en la
     *                                transacción (para validar surtidos globales)
     */
    private void processPromotions(Order order, List<UUID> promotionIds, int contextTotalNormalItems) {
        log.info("Procesando promociones. IDs recibidos del request: {}", promotionIds);

        // Contar instancias de cada promoción para asignar índices
        java.util.Map<UUID, Integer> promoIndexCount = new java.util.HashMap<>();

        promotionIds.forEach(id -> {
            log.info("Buscando promoción (Normal o Especial) con ID: {}", id);

            // 1. INTENTAR COMO SPECIAL PROMOTION
            // ✅ CRÍTICO: Usar repositorio directamente para evitar excepciones que marcan la transacción para rollback
            SpecialPromotion specialPromotion = null;
            Promotion promotion = null;
            boolean isSpecial = false;

            // ✅ Buscar primero en SpecialPromotion usando Optional (no lanza excepción)
            java.util.Optional<SpecialPromotion> specialPromotionOpt = specialPromotionRepository.findById(id);

            if (specialPromotionOpt.isPresent()) {
                specialPromotion = specialPromotionOpt.get();
                isSpecial = true;
                log.info("✅ Encontrada como SpecialPromotion: {}", specialPromotion.getNombre());
            } else {
                log.info("No es SpecialPromotion, buscando como Promotion normal...");
            }

            if (isSpecial && specialPromotion != null) {
                // ES UNA PROMOCIÓN ESPECIAL
                if (!specialPromotion.isActive()) {
                    throw new BusinessExeption(
                            "La promoción especial '" + specialPromotion.getNombre() + "' no está activa");
                }

                // Determinar la Promoción "Lógica" (Padre o Standalone)
                if (specialPromotion.isLinked()) {
                    promotion = specialPromotion.getParentPromotion();
                } else {
                    // Standalone: Por ahora no soportado totalmente sin duplicar lógica.
                    // Asumiremos que SpecialPromotion SIEMPRE está vinculada por ahora según
                    // requerimiento.
                    // Si no, necesitaríamos mapear SpecialPromotion a una estructura compatible.
                    // FALLBACK: Usar lógica de Promotion si SpecialPromotion tiene los campos
                    // necesarios mapeados.
                    // Dado el tiempo, lanzaré error si no está vinculada para forzar el uso
                    // correcto.
                    throw new BusinessExeption(
                            "Promociones especiales Standalone no implementadas totalmente en pedidos aun.");
                }

                log.info("✅ Usando SpecialPromotion: {} (Padre: {})", specialPromotion.getNombre(),
                        promotion.getNombre());

            } else {
                // ES UNA PROMOCIÓN NORMAL
                promotion = promotionService.findEntityById(id);
            }

            // Validaciones comunes de la promoción (Padre o Normal)
            // Validar vigencia
            if (!promotion.isValid()) {
                throw new BusinessExeption("La promoción '" + promotion.getNombre() + "' no está vigente");
            }

            if (!Boolean.TRUE.equals(promotion.getActive())) {
                throw new BusinessExeption("La promoción '" + promotion.getNombre() + "' no está activa");
            }

            // ✅ NUEVO: Generar UUID único para esta instancia de promoción
            UUID promotionInstanceId = java.util.UUID.randomUUID();

            // ✅ NUEVO: Calcular índice ordinal para promociones duplicadas
            int groupIndex = promoIndexCount.getOrDefault(id, 0) + 1;
            promoIndexCount.put(id, groupIndex);

            // ✅ NUEVO: Obtener precio efectivo
            // Si es Special, tiene prioridad su precio si está definido
            BigDecimal effectivePrice;
            if (isSpecial && specialPromotion.getPackPrice() != null) {
                effectivePrice = specialPromotion.getPackPrice();
            } else {
                effectivePrice = promotion.getPackPrice() != null
                        ? promotion.getPackPrice()
                        : (promotion.getMainProduct() != null
                                ? promotion.getMainProduct().getPrecio()
                                        .multiply(BigDecimal.valueOf(promotion.getBuyQuantity()))
                                : BigDecimal.ZERO);
            }

            log.info("📍 Promoción '{}' - Instancia: {} (grupo #{}), Precio Efectivo: ${}",
                    isSpecial ? specialPromotion.getNombre() : promotion.getNombre(),
                    promotionInstanceId, groupIndex, effectivePrice);

            // Variables finales para usar en lambdas/builders
            final SpecialPromotion finalSpecialPromotion = specialPromotion;
            final Promotion finalPromotion = promotion;

            // ==========================================
            // Lógica Diferenciada: SURTIDA vs PREDEFINIDA
            // ==========================================

            if (promotion.isAssortment()) {
                // =========================
                // CASO 1: PROMOCIÓN SURTIDA (Mix & Match / BUY_GET_FREE)
                // =========================

                // Validamos que la cantidad total cumpla el requisito
                if (contextTotalNormalItems < promotion.getBuyQuantity()) {
                    throw new BusinessExeption("Para aplicar la promoción '" + promotion.getNombre() +
                            "' debe agregar al menos " + promotion.getBuyQuantity() + " productos a la orden.");
                }

                // ITEMS COMPRADOS:
                // ✅ NUEVO: Crear item del mainProduct si existe
                Product mainProduct = promotion.getMainProduct();
                if (mainProduct != null) {
                    OrderItem mainItem = OrderItem.builder()
                            .product(mainProduct)
                            .cantidad(promotion.getBuyQuantity())
                            .precioUnitario(mainProduct.getPrecio())
                            .subTotal(mainProduct.getPrecio().multiply(BigDecimal.valueOf(promotion.getBuyQuantity())))
                            .promotion(promotion)
                            .isPromotionItem(true)
                            .isFreeItem(false)
                            .promotionInstanceId(promotionInstanceId)
                            .promotionPackPrice(null) // Surtida: null para usar subTotal
                            .promotionGroupIndex(groupIndex)
                            .specialPromotion(finalSpecialPromotion) // ✅ Link Special Promotion
                            .build();

                    // ✅ Descontar stock del mainProduct
                    mainProduct.decreaseStock(promotion.getBuyQuantity());
                    log.info("⬇️ Stock descontado para mainProduct surtido '{}': -{} (stock actual: {})",
                            mainProduct.getNombre(), promotion.getBuyQuantity(), mainProduct.getStock());

                    if (mainProduct.getStock() < 0) {
                        mainItem.setOutOfStock(true);
                        log.warn("⚠️ Stock NEGATIVO para mainProduct surtido '{}': {}",
                                mainProduct.getNombre(), mainProduct.getStock());
                    }

                    order.addItem(mainItem);
                }

                // ITEMS DE REGALO (Bonificados):
                // Agregamos los items de regalo definidos como placeholders
                if (promotion.getGiftItems() != null) {
                    for (org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem gift : promotion
                            .getGiftItems()) {
                        Product freeProduct = gift.getProduct();
                        Integer qty = gift.getQuantity();

                        // ✅ NUEVO: Crear item con identificadores únicos de promoción
                        OrderItem placeholderItem = OrderItem.builder()
                                .product(freeProduct)
                                .cantidad(qty)
                                .precioUnitario(BigDecimal.ZERO) // GRATIS
                                .subTotal(BigDecimal.ZERO)
                                .promotion(promotion)
                                .isPromotionItem(true)
                                .isFreeItem(true)
                                .promotionInstanceId(promotionInstanceId)
                                .promotionPackPrice(null) // Regalos surtidos: null para no afectar recalculo
                                .promotionGroupIndex(groupIndex)
                                .specialPromotion(finalSpecialPromotion) // ✅ Link Special Promotion
                                .build();

                        // ✅ DESCUENTO DE STOCK: Permitir stock negativo (sin restricción)
                        freeProduct.decreaseStock(qty);
                        log.info("⬇️ Stock descontado para regalo surtido '{}': -{} (stock actual: {})",
                                freeProduct.getNombre(), qty, freeProduct.getStock());

                        if (freeProduct.getStock() < 0) {
                            placeholderItem.setOutOfStock(true);
                            log.warn("⚠️ Stock NEGATIVO para regalo surtido '{}': {}",
                                    freeProduct.getNombre(), freeProduct.getStock());
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
                    // ✅ NUEVO: Agregar el producto principal (Pack) con precio fijo guardado
                    OrderItem buyItem = OrderItem.builder()
                            .product(mainProduct)
                            .cantidad(promotion.getBuyQuantity())
                            .precioUnitario(mainProduct.getPrecio())
                            .subTotal(effectivePrice)
                            .promotion(promotion)
                            .isPromotionItem(true)
                            .isFreeItem(false)
                            .promotionInstanceId(promotionInstanceId)
                            .promotionPackPrice(effectivePrice) // ✅ PRECIO EFECTIVO (override o normal)
                            .promotionGroupIndex(groupIndex)
                            .specialPromotion(finalSpecialPromotion) // ✅ Link Special Promotion
                            .build();

                    // ✅ DESCUENTO DE STOCK: Permitir stock negativo
                    mainProduct.decreaseStock(promotion.getBuyQuantity());
                    log.info("⬇️ Stock descontado para producto principal '{}': -{} (stock actual: {})",
                            mainProduct.getNombre(), promotion.getBuyQuantity(), mainProduct.getStock());

                    if (mainProduct.getStock() < 0) {
                        buyItem.setOutOfStock(true);
                        log.warn("⚠️ Stock NEGATIVO para producto principal '{}': {}",
                                mainProduct.getNombre(), mainProduct.getStock());
                    }

                    order.addItem(buyItem);
                }

                // ✅ CRÍTICO: Descontar stock de todos los productos en giftItems
                if (promotion.getGiftItems() != null) {
                    for (org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem gift : promotion
                            .getGiftItems()) {
                        Product freeProduct = gift.getProduct();
                        Integer qty = gift.getQuantity();

                        // ✅ NUEVO: Crear item con identificadores únicos de promoción
                        OrderItem freeItem = OrderItem.builder()
                                .product(freeProduct)
                                .cantidad(qty)
                                .precioUnitario(BigDecimal.ZERO)
                                .subTotal(BigDecimal.ZERO)
                                .promotion(promotion)
                                .isPromotionItem(true)
                                .isFreeItem(true)
                                .promotionInstanceId(promotionInstanceId)
                                .promotionPackPrice(BigDecimal.ZERO) // Regalos siempre $0
                                .promotionGroupIndex(groupIndex)
                                .specialPromotion(finalSpecialPromotion) // ✅ Link Special Promotion
                                .build();

                        // ✅ DESCUENTO DE STOCK: Permitir stock negativo
                        freeProduct.decreaseStock(qty);
                        log.info("⬇️ Stock descontado para regalo '{}': -{} (stock actual: {})",
                                freeProduct.getNombre(), qty, freeProduct.getStock());

                        if (freeProduct.getStock() < 0) {
                            freeItem.setOutOfStock(true);
                            log.warn("⚠️ Stock NEGATIVO para regalo '{}': {}",
                                    freeProduct.getNombre(), freeProduct.getStock());
                        } else if (freeProduct.getStock() < qty) {
                            log.warn("⚠️ Stock insuficiente para regalo fijo '{}'. Disponible: {}, Requerido: {}",
                                    freeProduct.getNombre(), freeProduct.getStock() + qty, qty);
                        }

                        order.addItem(freeItem);
                    }
                }
            }

            log.info("✅ Promoción '{}' aplicada correctamente con instancia {}", promotion.getNombre(),
                    promotionInstanceId);
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

            // Set completion date
            order.setCompletedAt(java.time.LocalDateTime.now());

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

        // Check if this is a shared user (NinaTorres/YicelaSandoval)
        if (UserUnificationUtil.isSharedUser(username)) {
            // Get orders for both shared usernames
            List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(username);
            return ordenRepository.findByVendedorUsernameIn(sharedUsernames)
                    .stream()
                    .map(orderMapper::toResponse)
                    .toList();
        }

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

        // VALIDAR QUE HAYA ITEMS O PROMOCIONES O BONIFICADOS
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        boolean hasPromotions = request.promotionIds() != null && !request.promotionIds().isEmpty();
        boolean hasBonifiedItems = request.bonifiedItems() != null && !request.bonifiedItems().isEmpty();

        if (!hasItems && !hasPromotions && !hasBonifiedItems) {
            throw new BusinessExeption("La orden debe tener al menos un producto, una promoción o productos bonificados");
        }

        // CAPTURAR IDs DE PROMOCIONES ACTUALES **ANTES** DE LIMPIAR ITEMS
        // Esto es CRÍTICO para comparar correctamente si las promociones cambiaron
        // ✅ CORRECCIÓN: Usar List y agrupar por InstanceId para soportar múltiples
        // instancias de la misma promo
        // Nota: Si una promoción antigua no tiene InstanceId, se agrupará aparte (UUID
        // random).
        // En la práctica, esto forzará una actualización la primera vez, migrando a
        // items con InstanceId.
        java.util.Map<UUID, OrderItem> uniqueInstances = new java.util.HashMap<>();

        order.getItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem()) && i.getPromotion() != null)
                .forEach(i -> {
                    UUID key = i.getPromotionInstanceId() != null
                            ? i.getPromotionInstanceId()
                            : java.util.UUID.randomUUID(); // Force distinct instance for legacy items to ensure they
                                                           // are counted
                    uniqueInstances.putIfAbsent(key, i);
                });

        java.util.List<UUID> currentPromotionIds = uniqueInstances.values().stream()
                .map(i -> i.getSpecialPromotion() != null ? i.getSpecialPromotion().getId() : i.getPromotion().getId())
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        // CAPTURAR items que se van a PRESERVAR (para NO restaurar su stock)
        boolean hasNewFreightItems = request.items() != null &&
                request.items().stream().anyMatch(i -> Boolean.TRUE.equals(i.isFreightItem()));

        Set<UUID> idsToPreserve = new java.util.HashSet<>();

        for (OrderItem item : order.getItems()) {
            // Preservar items de promoción
            if (Boolean.TRUE.equals(item.getIsPromotionItem())) {
                idsToPreserve.add(item.getId());
            }
            // Preservar items de flete si NO hay nuevos
            if (Boolean.TRUE.equals(item.getIsFreightItem()) && !hasNewFreightItems) {
                idsToPreserve.add(item.getId());
            }
        }

        // RESTAURAR STOCK de items anteriores (normales, bonificados, flete que se reemplazan)
        // NO restaurar items de promoción ni items de flete que se van a preservar
        order.getItems().forEach(item -> {
            Product product = item.getProduct();

            // No restaurar items que se van a preservar
            if (idsToPreserve.contains(item.getId())) {
                log.info("Item preservado (no restaura stock): {} - tipo: {}",
                    product.getNombre(),
                    Boolean.TRUE.equals(item.getIsPromotionItem()) ? "PROMO" :
                    Boolean.TRUE.equals(item.getIsFreightItem()) ? "FLETE" : "OTRO");
                return;
            }

            // No restaurar stock de items regalados (isFreeItem=true) - estos son de promo
            if (Boolean.TRUE.equals(item.getIsFreeItem())) {
                log.info("Item de regalo no restaura stock: {}", product.getNombre());
                return;
            }

            // ✅ RESTAURAR items normales, bonificados y de flete que se van a reemplazar
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

                if (Boolean.TRUE.equals(item.getIsBonified())) {
                    log.info("✅ Stock restaurado (BONIFICADO) en edición para '{}': +{}", product.getNombre(), stockToRestore);
                } else if (Boolean.TRUE.equals(item.getIsFreightItem())) {
                    log.info("✅ Stock restaurado (FLETE-REEMPLAZADO) en edición para '{}': +{}", product.getNombre(), stockToRestore);
                } else {
                    log.info("✅ Stock restaurado (NORMAL) en edición para '{}': +{}", product.getNombre(), stockToRestore);
                }
            }
        });

        // Limpiar SOLO items no promocionados ni de flete (preservar items de promo y
        // flete)
        List<OrderItem> promotionItems = new java.util.ArrayList<>();
        List<OrderItem> freightItems = new java.util.ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (Boolean.TRUE.equals(item.getIsPromotionItem())) {
                promotionItems.add(item);
            }
            // IMPORTANTE: Solo preservar items de flete si NO hay nuevos items de flete en
            // la solicitud
            if (Boolean.TRUE.equals(item.getIsFreightItem()) && (request.items() == null ||
                    request.items().stream().noneMatch(i -> Boolean.TRUE.equals(i.isFreightItem())))) {
                freightItems.add(item);
            }
        }
        order.clearItems();

        // ✅ ACTUALIZADO: Re-agregar items de promoción preservando precios y IDs únicos
        for (OrderItem promoItem : promotionItems) {
            // Los promocionPackPrice y promotionInstanceId ya están guardados en el item
            order.addItem(promoItem);
            log.info("✅ Item de promoción re-agregado - Instancia: {} - Precio: ${}",
                    promoItem.getPromotionInstanceId(), promoItem.getPromotionPackPrice());
        }

        // Re-agregar items de flete para preservar configuración (solo si NO hay
        // nuevos)
        for (OrderItem freightItem : freightItems) {
            order.addItem(freightItem);
        }

        // DETECTAR TIPO DE ORDEN - Usar items de promoción REALES, no solo notas
        // Las notas pueden estar vacías o ser modificadas por el usuario
        String currentNotes = order.getNotas() != null ? order.getNotas() : "";

        // Detectar si REALMENTE es orden de promoción verificando si tiene items de
        // promo
        boolean isPromoOrder = !currentPromotionIds.isEmpty() || hasPromotions;

        log.info("📝 Orden {}: Notas='{}', tieneItemsPromo={}, tienePromoIdsEnRequest={}, esPromocion={}",
                orderId, currentNotes, !currentPromotionIds.isEmpty(), hasPromotions, isPromoOrder);

        // AGREGAR NUEVOS ITEMS (con validación de stock y split)
        // IMPORTANTE:
        // - NO procesar items de flete como items normales
        // - Si es orden Promo: NO agregar items normales
        // - Si es orden Normal/S/R: Agregar todos los items excepto flete
        if (hasItems) {
            log.info("📦 Request tiene {} items totales", request.items().size());

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

            log.info("📦 Items filtrados: {} normales, {} flete", normalItemsReq.size(), freightItemsReq.size());

            // Procesar items de flete (PRIMERO, antes que los items normales)
            if (!freightItemsReq.isEmpty()) {
                processFreightItems(order, freightItemsReq);
                log.info("Items de flete procesados en edición de orden {}: {} items", orderId, freightItemsReq.size());
            }

            // Procesar items normales (no flete)
            normalItemsReq.forEach(itemReq -> {
                // ❌ BLOQUEAR items normales en orden de Promo
                if (isPromoOrder) {
                    log.info("⚠️ BLOQUEADO: Item normal ignorado en edición de orden promo: {} (cantidad: {})",
                            itemReq.productId(), itemReq.cantidad());
                    return;
                }

                Product product = productService.findEntityById(itemReq.productId());

                // En edición (Admin), SIEMPRE permitimos out of stock implícitamente si se
                // desea, pero mantenemos coherencia de inventario

                int requestedQuantity = itemReq.cantidad();
                int currentStock = product.getStock();
                boolean hasStock = currentStock >= requestedQuantity;

                // LÓGICA UNIFICADA: SIEMPRE VENDER EN UNA SOLA LÍNEA (Stock negativo permitido)
                OrderItem item = new OrderItem(product, requestedQuantity);

                if (hasStock) {
                    item.setOutOfStock(false);
                } else {
                    item.setOutOfStock(true);
                    log.info("Producto {} vendido sin stock suficiente en edición. Stock actual: {}, Solicitado: {}",
                            product.getNombre(), currentStock, requestedQuantity);
                }

                // Siempre descontamos todo
                item.setCantidadDescontada(requestedQuantity);
                item.setCantidadPendiente(0);

                product.decreaseStock(requestedQuantity);
                order.addItem(item);
            });
        } else if (isPromoOrder && hasItems) {
            log.debug("Edición de orden de promo: Se ignoran items normales (solo se preservan regalos y flete)");
        }

        // PROCESAR BONIFICADOS (si la orden NO es de promo)
        if (!isPromoOrder && request.bonifiedItems() != null && !request.bonifiedItems().isEmpty()) {
            processBonifiedItems(order, request.bonifiedItems());
        }

        // PROCESAR PROMOCIONES - Solo si están cambiando
        // 1. Obtener IDs solicitados (puede ser lista vacía si se borraron todas)
        java.util.List<UUID> requestedPromotionIds = request.promotionIds() != null
                ? request.promotionIds().stream().sorted().collect(java.util.stream.Collectors.toList())
                : new java.util.ArrayList<>();

        // 2. Verificar si hubo cambios
        if (!currentPromotionIds.equals(requestedPromotionIds)) {
            log.info("Promociones cambiaron en orden {}: {} -> {}", orderId, currentPromotionIds,
                    requestedPromotionIds);

            // 3. Identificar items de promoción actuales para REMOVER y RESTAURAR STOCK
            List<OrderItem> promoItemsToRemove = order.getItems().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getIsPromotionItem()))
                    .toList();

            // 4. ✅ RESTAURAR STOCK de las promociones que se van a eliminar
            if (!promoItemsToRemove.isEmpty()) {
                restoreStockForItems(promoItemsToRemove, order.getItems());
                log.info("Stock restaurado para {} items de promoción eliminados", promoItemsToRemove.size());
            }

            // 5. Eliminar items de la orden
            // Usar removeAll para borrar la colección exacta
            order.getItems().removeAll(promoItemsToRemove);

            // 6. Agregar nuevas promociones (si hay)
            if (!requestedPromotionIds.isEmpty()) {
                int totalNormalItemsCount = order.getItems().stream()
                        .filter(i -> !Boolean.TRUE.equals(i.getIsPromotionItem()))
                        .mapToInt(OrderItem::getCantidad)
                        .sum();
                processPromotions(order, requestedPromotionIds, totalNormalItemsCount);
                log.info("Promociones actualizadas en edición de orden {}: {} items de promo creados",
                        orderId, requestedPromotionIds.size());
            }

        } else {
            log.info("Promociones sin cambios en edición de orden {}: {} - Items preservados (no re-procesados)",
                    orderId, currentPromotionIds);
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

        // Actualizar flete - ⛔ NO permitir flete en órdenes de promoción
        // Usar variable separada para no romper el lambda anterior que usa isPromoOrder
        boolean isPromoForFreight = isPromoOrder
                || (order.getNotas() != null && order.getNotas().contains("[Promoción]"));
        if (isPromoForFreight) {
            // Las órdenes de promoción NUNCA deben tener flete
            order.setIncludeFreight(false);
            order.setIsFreightBonified(false);
            order.setFreightCustomText(null);
            order.setFreightQuantity(1);
            log.info("Flete ignorado para orden de promoción: {}", order.getId());
        } else if (Boolean.TRUE.equals(request.includeFreight())) {
            order.setIncludeFreight(true);
            order.setIsFreightBonified(Boolean.TRUE.equals(request.isFreightBonified()));

            // Construir descripción de flete personalizado (solo texto)
            if (request.freightCustomText() != null) {
                order.setFreightCustomText(request.freightCustomText());
            }

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

        // ✅ Restaurar Stock de TODOS los items usando método reutilizable
        restoreStockForItems(order.getItems(), order.getItems());

        order.setEstado(OrdenStatus.ANULADA);
        order.setCancellationReason(reason);
        ordenRepository.save(order);

        log.info("✅ Orden {} anulada completamente. Stock restaurado.", orderId);
    }

    /**
     * Restaura el stock de una lista de items.
     * Útil para anulación de órdenes y para edición (cuando se eliminan
     * productos/promos).
     * 
     * @param itemsToRestore Lista de items cuyo stock se debe restaurar
     * @param contextItems   Lista completa de items de la orden (contexto para
     *                       buscar items relacionados, como regalos separados)
     */
    private void restoreStockForItems(List<OrderItem> itemsToRestore, List<OrderItem> contextItems) {
        for (OrderItem item : itemsToRestore) {
            if (item.getProduct() != null) {
                Product product = item.getProduct();

                // Ignorar sistema product y ocultos (si aplica)
                if ("SURTIDO PROMOCIONAL".equals(product.getNombre()) || product.isHidden()) {
                    continue;
                }

                log.info("🔄 Procesando restauración de item: {}, isPromo={}, isFree={}, isBonified={}",
                        product.getNombre(), item.getIsPromotionItem(), item.getIsFreeItem(), item.getIsBonified());

                // ✅ CASO 1: Items normales (no promo, no bonificado, no flete)
                if (!Boolean.TRUE.equals(item.getIsPromotionItem()) &&
                        !Boolean.TRUE.equals(item.getIsBonified()) &&
                        !Boolean.TRUE.equals(item.getIsFreightItem())) {
                    product.increaseStock(item.getCantidad());
                    log.info("✅ Stock restaurado (NORMAL) para '{}': +{}", product.getNombre(), item.getCantidad());
                }

                // ✅ CASO 2: Bonificados puros (restaurar solo lo que se descontó)
                else if (Boolean.TRUE.equals(item.getIsBonified()) &&
                        !Boolean.TRUE.equals(item.getIsPromotionItem())) {
                    Integer cantidadDescontada = item.getCantidadDescontada() != null ? item.getCantidadDescontada()
                            : item.getCantidad();
                    product.increaseStock(cantidadDescontada);
                    log.info("✅ Stock restaurado (BONIFICADO) para '{}': +{}", product.getNombre(), cantidadDescontada);
                }

                // ✅ CASO 3: Items de regalo de promoción (isFreeItem=true)
                // ✅ NUEVO: Restaurar TODOS los items que son regalos
                else if (Boolean.TRUE.equals(item.getIsPromotionItem()) &&
                        Boolean.TRUE.equals(item.getIsFreeItem())) {
                    // Los regalos siempre se venden a precio 0, restaurar cantidad completa
                    product.increaseStock(item.getCantidad());
                    log.info("✅ Stock restaurado (PROMO GIFT - Instancia {}) para '{}': +{}",
                            item.getPromotionInstanceId(), product.getNombre(), item.getCantidad());
                }

                // ✅ CASO 4: Items de promoción mainProduct
                else if (Boolean.TRUE.equals(item.getIsPromotionItem()) &&
                        !Boolean.TRUE.equals(item.getIsFreeItem())) {

                    // 4A. Restaurar mainProduct de ESTA instancia
                    product.increaseStock(item.getCantidad());
                    log.info("✅ Stock restaurado (PROMO MAIN - Instancia {}) para '{}': +{}",
                            item.getPromotionInstanceId(), product.getNombre(), item.getCantidad());

                    // 4B. ✅ También restaurar los regalos vinculados en giftItems
                    // (por si existen como referencia)
                    // ✅ IMPORTANTE: Obtener la promoción correcta (padre si es SpecialPromotion)
                    org.example.sistema_gestion_vitalexa.entity.Promotion promoForGifts = null;

                    try {
                        if (item.getSpecialPromotion() != null && item.getSpecialPromotion().getParentPromotion() != null) {
                            promoForGifts = item.getSpecialPromotion().getParentPromotion();
                        } else if (item.getPromotion() != null) {
                            promoForGifts = item.getPromotion();
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ No se pudo cargar la promoción para restaurar gifts del item {}: {}",
                                item.getId(), e.getMessage());
                    }

                    if (promoForGifts != null && promoForGifts.getGiftItems() != null) {
                        for (org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem gift : promoForGifts
                                .getGiftItems()) {

                            Product giftProduct = gift.getProduct();
                            Integer giftQty = gift.getQuantity();

                            // ✅ Restaurar solo si NO hay un item separado isFreeItem
                            // (para evitar doble restauración). Buscamos en el CONTEXTO (toda la orden).
                            boolean hasSeparateGiftItem = contextItems.stream()
                                    .anyMatch(i -> Boolean.TRUE.equals(i.getIsPromotionItem()) &&
                                            Boolean.TRUE.equals(i.getIsFreeItem()) &&
                                            i.getProduct().getId().equals(giftProduct.getId()) &&
                                            i.getPromotionInstanceId() != null &&
                                            i.getPromotionInstanceId().equals(item.getPromotionInstanceId()));

                            if (!hasSeparateGiftItem) {
                                giftProduct.increaseStock(giftQty);
                                log.info("✅ Stock restaurado (PROMO GIFT ref - Instancia {}) para '{}': +{}",
                                        item.getPromotionInstanceId(), giftProduct.getNombre(), giftQty);
                            } else {
                                log.info("⏭️  Gift item separado encontrado para '{}', restauración en CASO 3",
                                        giftProduct.getNombre());
                            }
                        }
                    }
                }

                // ✅ CASO 5: Items de flete (restaurar solo lo que se descontó)
                else if (Boolean.TRUE.equals(item.getIsFreightItem())) {
                    Integer cantidadDescontada = item.getCantidadDescontada() != null
                        ? item.getCantidadDescontada()
                        : item.getCantidad();

                    if (cantidadDescontada > 0) {
                        product.increaseStock(cantidadDescontada);
                        log.info("✅ Stock restaurado (FLETE) para '{}': +{}",
                            product.getNombre(), cantidadDescontada);
                    }
                }
            }
        }
    }

    /**
     * Procesar productos bonificados (regalos) de una orden
     * ✅ Los bonificados siempre tienen precio 0 y permiten stock negativo
     */
    private void processBonifiedItems(Order order, List<BonifiedItemRequestDTO> bonifiedItems) {
        if (bonifiedItems == null || bonifiedItems.isEmpty()) {
            return;
        }

        bonifiedItems.forEach(itemReq -> {
            Product product = productService.findEntityById(itemReq.productId());
            int requestedQuantity = itemReq.cantidad();

            // Crear item bonificado
            OrderItem item = new OrderItem(product, requestedQuantity);
            item.setIsBonified(true);
            item.setPrecioUnitario(BigDecimal.ZERO);
            item.setSubTotal(BigDecimal.ZERO);

            // ✅ IGUAL QUE PRODUCTOS NORMALES: Siempre descontar la cantidad completa (permite stock negativo)
            item.setCantidadDescontada(requestedQuantity);
            item.setCantidadPendiente(0);
            item.setOutOfStock(false);

            // Descontar stock (permite negativo)
            product.decreaseStock(requestedQuantity);

            order.addItem(item);

            log.info("✅ Producto bonificado {} agregado: cantidad={}, stock resultante={}",
                    product.getNombre(), requestedQuantity, product.getStock());
        });
    }

    // ...existing code...

    @Override
    @Transactional
    public OrderResponse updateHistoricalInvoice(UUID orderId, CreateHistoricalInvoiceRequest request,
            String username) {
        // Validar permisos (Owner o Admin)
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        if (currentUser.getRole() != org.example.sistema_gestion_vitalexa.enums.Role.OWNER &&
                currentUser.getRole() != org.example.sistema_gestion_vitalexa.enums.Role.ADMIN) {
            throw new BusinessExeption("Solo Owner o Admin pueden editar facturas históricas");
        }

        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        // Validar unicidad de número de factura si cambió
        if (order.getInvoiceNumber() != null && !order.getInvoiceNumber().equals(request.invoiceNumber())) {
            if (ordenRepository.findByInvoiceNumber(request.invoiceNumber()).isPresent()) {
                throw new BusinessExeption("Ya existe una factura con el número: " + request.invoiceNumber());
            }
        } else if (order.getInvoiceNumber() == null) {
            // Caso raro, pero posible
            if (ordenRepository.findByInvoiceNumber(request.invoiceNumber()).isPresent()) {
                throw new BusinessExeption("Ya existe una factura con el número: " + request.invoiceNumber());
            }
        }

        // Validar montos
        if (request.amountPaid().compareTo(request.totalValue()) > 0) {
            throw new BusinessExeption("El monto pagado no puede ser mayor al valor total");
        }

        // Actualizar datos básicos
        order.setFecha(request.fecha());
        order.setTotal(request.totalValue());
        order.setInvoiceNumber(request.invoiceNumber());
        // Estado sigue siendo COMPLETADO (o lo forzamos por si acaso)
        order.setEstado(OrdenStatus.COMPLETADO);

        // Actualizar Cliente y Vendedor
        Client client = null;
        if (request.clientId() != null) {
            client = clientService.findEntityById(request.clientId());
        }

        // Lógica de asignación de vendedor (igual que en create)
        User owner = userRepository.findAll().stream()
                .filter(u -> u.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.OWNER)
                .findFirst()
                .orElse(currentUser); // Fallback al usuario actual si no halla owner explicito

        User vendedor = owner;
        if (client != null && client.getVendedorAsignado() != null) {
            vendedor = client.getVendedorAsignado();
        } else if (client != null) {
            // Cliente sin vendedor -> Owner
        }

        // Actualizar cliente y vendedor en orden
        order.setCliente(client);
        order.setVendedor(vendedor);

        // Reconstruir Notas
        BigDecimal amountDue = request.totalValue().subtract(request.amountPaid());
        StringBuilder notes = new StringBuilder("[HISTÓRICA] [EDITADA] ");
        notes.append("Tipo: ").append(request.invoiceType().getLabel()).append(" | ");
        notes.append("Vendedor: ").append(vendedor.getUsername()).append(" | ");

        if (request.clientName() != null)
            notes.append("Cliente: ").append(request.clientName()).append(" | ");
        if (request.clientPhone() != null)
            notes.append("Tel: ").append(request.clientPhone()).append(" | ");
        if (request.clientEmail() != null)
            notes.append("Email: ").append(request.clientEmail()).append(" | ");
        if (request.clientAddress() != null)
            notes.append("Dir: ").append(request.clientAddress()).append(" | ");

        notes.append("Pagado: $").append(request.amountPaid())
                .append(" | Debe: $").append(amountDue);

        // NO agregamos request.notes() aquí para evitar duplicación
        // El usuario maneja la nota completa en el frontend
        notes.append(" ").append(request.invoiceType().getSuffix());

        order.setNotas(notes.toString());

        // Actualizar compras del cliente (restar anterior, sumar nueva??)
        // La lógica de `registerPurchase` SUMA. Deberíamos recalcular?
        // Es complejo recalcular el acumulado exacto.
        // Opción: No tocar el acumulado aquí, asumiendo que el "Total Compras" dinámico
        // lo arregla.
        // COMO IMPLEMENTAMOS "Total Compras" dinámico en la sesión anterior,
        // NO necesitamos actualizar manualmente `client.totalCompras`.
        // El nuevo mapper calcula SUM(Orders Completed). Al guardar esta orden con
        // nuevo total,
        // el cálculo dinámico se arregla solo. ¡Excelente!

        Order savedOrder = ordenRepository.save(order);

        // Actualizar Pagos
        // Estrategia: Borrar pagos anteriores y crear uno nuevo con el nuevo monto
        // (Simplificación válida para facturas históricas manuales)
        paymentRepository.deleteByOrder(savedOrder);
        paymentRepository.flush(); // Forzar borrado inmediato

        if (request.amountPaid().compareTo(BigDecimal.ZERO) > 0) {
            org.example.sistema_gestion_vitalexa.entity.Payment payment = org.example.sistema_gestion_vitalexa.entity.Payment
                    .builder()
                    .order(savedOrder)
                    .amount(request.amountPaid())
                    .paymentDate(request.fecha())
                    .withinDeadline(true)
                    .discountApplied(BigDecimal.ZERO)
                    .registeredBy(currentUser)
                    .notes("[HISTÓRICA-EDIT] Pago actualizado")
                    .build();
            paymentRepository.save(payment);
        }

        // 📊 RECALCULAR METAS AFECTADAS
        // Dado que puede haber cambiado el vendedor, fecha o monto,
        // recalculamos completamente las metas afectadas

        // Si el vendedor cambió, necesitamos recalcular la meta del vendedor ANTERIOR
        // también
        // Pero no tenemos tracking del vendedor anterior aquí, así que recalcularemos
        // solo la meta del vendedor actual. Si cambió el vendedor, el admin debe
        // verificar manualmente.

        LocalDate invoiceDate = request.fecha().toLocalDate();

        // Recalcular meta del vendedor actual para el mes/año de la factura
        saleGoalService.recalculateGoalForVendorMonth(
                vendedor.getId(),
                invoiceDate.getMonthValue(),
                invoiceDate.getYear());

        log.info("Meta recalculada para vendedor {} en {}/{} tras editar factura histórica {}",
                vendedor.getUsername(), invoiceDate.getMonthValue(), invoiceDate.getYear(),
                request.invoiceNumber());

        log.info("Factura histórica actualizada: {}", request.invoiceNumber());
        return orderMapper.toResponse(savedOrder);
    }

    // =========================
    // ELIMINAR ITEMS / PROMOCIONES
    // =========================

    /**
     * ✅ NUEVO: Eliminar un item (promoción/bonificado) de una orden
     * Permite eliminar promociones individuales sin afectar el resto
     */
    @Override
    @Transactional
    public OrderResponse deleteOrderItem(UUID orderId, UUID itemId) {
        log.info("🗑️  Eliminando item {} de orden {}", itemId, orderId);

        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        if (order.getEstado() == OrdenStatus.COMPLETADO ||
                order.getEstado() == OrdenStatus.CANCELADO) {
            throw new BusinessExeption("No se puede editar una orden completada o cancelada");
        }

        // Buscar el item a eliminar
        OrderItem itemToDelete = order.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessExeption("Item no encontrado en la orden"));

        log.info("📍 Item encontrado: producto={}, cantidad={}, isPromo={}",
                itemToDelete.getProduct().getNombre(),
                itemToDelete.getCantidad(),
                itemToDelete.getIsPromotionItem());

        // Restaurar stock del producto
        Product product = itemToDelete.getProduct();

        // ✅ CRÍTICO: Guardar stock ANTES de restaurar
        Integer stockAnterior = product.getStock();

        // ✅ CASO 1: Items normales (no promoción, no bonificado)
        if (!Boolean.TRUE.equals(itemToDelete.getIsFreeItem()) &&
                !Boolean.TRUE.equals(itemToDelete.getIsPromotionItem())) {
            product.increaseStock(itemToDelete.getCantidad());
            log.info("✅ Stock restaurado para item normal '{}': +{}", product.getNombre(), itemToDelete.getCantidad());
        }

        // ✅ CASO 2: Bonificados puros (no son items de promoción)
        else if (Boolean.TRUE.equals(itemToDelete.getIsBonified()) &&
                !Boolean.TRUE.equals(itemToDelete.getIsPromotionItem()) &&
                itemToDelete.getCantidadDescontada() != null &&
                itemToDelete.getCantidadDescontada() > 0) {
            product.increaseStock(itemToDelete.getCantidadDescontada());
            log.info("✅ Stock restaurado para bonificado '{}': +{}",
                    product.getNombre(),
                    itemToDelete.getCantidadDescontada());
        }

        // ✅ CASO 3: Items de promoción (mainProduct o giftItems)
        else if (Boolean.TRUE.equals(itemToDelete.getIsPromotionItem())) {
            Integer qtyToRestore = itemToDelete.getCantidad();
            product.increaseStock(qtyToRestore);
            log.info("✅ Stock restaurado para item de promoción '{}': +{}", product.getNombre(), qtyToRestore);

            // ✅ CRÍTICO: Si es mainProduct de una promoción, también restaurar los
            // giftItems
            if (!Boolean.TRUE.equals(itemToDelete.getIsFreeItem()) &&
                    itemToDelete.getPromotion() != null &&
                    itemToDelete.getPromotion().getGiftItems() != null) {

                log.info("🔄 Restaurando stock de {} regalos de la promoción '{}'",
                        itemToDelete.getPromotion().getGiftItems().size(),
                        itemToDelete.getPromotion().getNombre());

                // Restaurar cada regalo de la promoción
                for (org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem gift : itemToDelete.getPromotion()
                        .getGiftItems()) {
                    Product giftProduct = gift.getProduct();
                    Integer giftQty = gift.getQuantity();

                    giftProduct.increaseStock(giftQty);
                    log.info("✅ Stock restaurado para regalo '{}': +{}",
                            giftProduct.getNombre(), giftQty);
                }
            }
        }

        // Eliminar el item de la orden
        order.removeItem(itemToDelete);

        // ✅ CRÍTICO: Registrar movimiento con stock CORRECTO (anterior y posterior)
        try {
            movementService.logMovement(
                    product,
                    org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType.ORDER_ITEM_REMOVAL,
                    itemToDelete.getCantidad(),
                    stockAnterior, // ✅ Stock ANTES de restaurar
                    product.getStock(), // Stock DESPUÉS de restaurar
                    "Eliminación de item/promoción de orden: " + orderId,
                    "System");
        } catch (Exception e) {
            log.warn("Error registrando movimiento de inventario: {}", e.getMessage());
        }

        // Guardar orden actualizada
        Order updatedOrder = ordenRepository.save(order);

        log.info("✅ Item {} eliminado correctamente. Total actualizado: ${}", itemId, updatedOrder.getTotal());

        return orderMapper.toResponse(updatedOrder);
    }

    /**
     * Crear factura histórica para cuadre de caja
     * (Solo Owner - para facturas anteriores al sistema)
     *
     * IMPORTANTE: La factura se asigna al VENDEDOR del cliente, no al Owner
     * - Si el cliente pertenece a VendedorX → la factura se registra como venta de
     * VendedorX
     * - Si el cliente no tiene vendedor asignado → se asigna al Owner (por defecto)
     */
    @Override
    public OrderResponse createHistoricalInvoice(
            org.example.sistema_gestion_vitalexa.dto.CreateHistoricalInvoiceRequest request,
            String ownerUsername) {

        // Validar que sea Owner
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        if (owner.getRole() != org.example.sistema_gestion_vitalexa.enums.Role.OWNER) {
            throw new BusinessExeption("Solo el Owner puede crear facturas históricas");
        }

        // Validar que el número de factura sea único
        if (ordenRepository.findByInvoiceNumber(request.invoiceNumber()).isPresent()) {
            throw new BusinessExeption("Ya existe una factura con el número: " + request.invoiceNumber());
        }

        // Validar que amountPaid no sea mayor al totalValue
        if (request.amountPaid().compareTo(request.totalValue()) > 0) {
            throw new BusinessExeption("El monto pagado ($" + request.amountPaid() +
                    ") no puede ser mayor al valor total ($" + request.totalValue() + ")");
        }

        // Obtener cliente si se proporciona ID
        Client client = null;
        if (request.clientId() != null) {
            client = clientService.findEntityById(request.clientId());
        } else if (request.clientName() != null) {
            log.info("Factura histórica sin cliente vinculado: {}", request.invoiceNumber());
        }

        // ✅ CLAVE: Asignar la factura al VENDEDOR DEL CLIENTE, no al Owner
        // Si el cliente pertenece a un vendedor → usar ese vendedor
        // Si no → usar el Owner como default
        User vendedor = owner; // Default es el Owner

        if (client != null && client.getVendedorAsignado() != null) {
            vendedor = client.getVendedorAsignado();
            log.info("Factura histórica asignada al vendedor del cliente: {}", vendedor.getUsername());
        } else if (client != null) {
            log.info("Cliente sin vendedor asignado. Factura se registra a nombre del Owner: {}", owner.getUsername());
        }

        // Crear orden sin items (es solo para registro)
        Order order = new Order(vendedor, client);
        order.setFecha(request.fecha());
        order.setTotal(request.totalValue());
        order.setInvoiceNumber(request.invoiceNumber());
        order.setEstado(OrdenStatus.COMPLETADO); // Factura histórica siempre está completada

        // Calcular lo que debe el cliente
        BigDecimal amountDue = request.totalValue().subtract(request.amountPaid());

        // Construir notas con información de la factura histórica
        StringBuilder notes = new StringBuilder("[HISTÓRICA] ");
        notes.append("Tipo: ").append(request.invoiceType().getLabel()).append(" | ");
        notes.append("Vendedor: ").append(vendedor.getUsername()).append(" | ");

        if (request.clientName() != null) {
            notes.append("Cliente: ").append(request.clientName()).append(" | ");
        }
        if (request.clientPhone() != null) {
            notes.append("Tel: ").append(request.clientPhone()).append(" | ");
        }
        if (request.clientEmail() != null) {
            notes.append("Email: ").append(request.clientEmail()).append(" | ");
        }
        if (request.clientAddress() != null) {
            notes.append("Dir: ").append(request.clientAddress()).append(" | ");
        }

        // Mostrar claramente: Pagado vs Debe
        notes.append("Pagado: $").append(request.amountPaid())
                .append(" | Debe: $").append(amountDue);

        if (request.notes() != null && !request.notes().isBlank()) {
            notes.append(" - ").append(request.notes());
        }

        // Agregar el suffix del tipo de factura
        notes.append(" ").append(request.invoiceType().getSuffix());

        order.setNotas(notes.toString());

        // Si el cliente existe, registrar la compra
        if (client != null) {
            client.registerPurchase(request.totalValue());
        }

        // Guardar orden
        Order savedOrder = ordenRepository.save(order);

        // 💳 Registrar el pago (si el cliente pagó algo)
        if (request.amountPaid().compareTo(BigDecimal.ZERO) > 0) {
            org.example.sistema_gestion_vitalexa.entity.Payment payment = org.example.sistema_gestion_vitalexa.entity.Payment
                    .builder()
                    .order(savedOrder)
                    .amount(request.amountPaid())
                    .paymentDate(request.fecha())
                    .withinDeadline(true) // Facturas históricas se asumen dentro de plazo
                    .discountApplied(BigDecimal.ZERO)
                    .registeredBy(owner)
                    .notes("[HISTÓRICA] Pago registrado de factura histórica")
                    .build();
            paymentRepository.save(payment);
            log.info("Pago registrado: ${} para factura histórica {}", request.amountPaid(), request.invoiceNumber());
        }

        // 🔄 SINCRONIZAR SECUENCIA DE FACTURAS
        // Asegurar que la secuencia esté siempre por delante de la factura más alta
        try {
            Long maxInvoice = ordenRepository.findMaxInvoiceNumber();
            // setval(..., val, false) hace que el siguiene nextval devuelva 'val'
            // Queremos que el siguiente sea max + 1
            ordenRepository.syncInvoiceSequence(maxInvoice + 1);
            log.info("Secuencia de facturas sincronizada. Próxima factura será: {}", maxInvoice + 1);
        } catch (Exception e) {
            log.error("Error sincronizando secuencia de facturas", e);
            // No fallamos la transacción por esto, pero es importante loguearlo
        }

        // 📊 ACTUALIZAR PROGRESO DE META DEL VENDEDOR
        // Si el vendedor tiene una meta para el mes/año de la factura histórica,
        // actualizarla para reflejar esta venta
        LocalDate invoiceDate = request.fecha().toLocalDate();
        saleGoalService.updateGoalProgress(
                vendedor.getId(),
                request.totalValue(),
                invoiceDate.getMonthValue(),
                invoiceDate.getYear());

        log.info("Factura histórica creada: {} | Monto: ${} | Pagado: ${} | Debe: ${} | Vendedor: {} | Owner: {}",
                request.invoiceNumber(),
                request.totalValue(),
                request.amountPaid(),
                amountDue,
                vendedor.getUsername(),
                ownerUsername);

        return orderMapper.toResponse(savedOrder);
    }
}
