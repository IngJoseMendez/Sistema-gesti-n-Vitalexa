package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.OrderItem;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.ProductRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.repository.PaymentRepository;
import org.example.sistema_gestion_vitalexa.service.ReportService;
import org.example.sistema_gestion_vitalexa.util.UserUnificationUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

        private final OrdenRepository ordenRepository;
        private final ProductRepository productRepository;
        private final ClientRepository clientRepository;
        private final UserRepository userRepository;
        private final PaymentRepository paymentRepository;

        @Override
        public ReportDTO getCompleteReport(LocalDate startDate, LocalDate endDate) {
                return new ReportDTO(
                                getSalesReport(startDate, endDate),
                                getProductReport(startDate, endDate),
                                getVendorReport(startDate, endDate),
                                getClientReport(startDate, endDate));
        }

        // fix
        @Override
        public ReportDTO getCompleteReport(LocalDate startDate, LocalDate endDate, UUID vendorId) {
                return new ReportDTO(
                                getSalesReport(startDate, endDate, vendorId),
                                getProductReport(startDate, endDate, vendorId),
                                getVendorReport(startDate, endDate), // El reporte de vendedores sigue mostrando ranking
                                                                     // general o filtramos? Por ahora general para
                                                                     // contexto
                                getClientReport(startDate, endDate, vendorId));
        }

        @Override
        public SalesReportDTO getSalesReport(LocalDate startDate, LocalDate endDate) {
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);
                List<Order> orders = ordenRepository.findByFechaBetween(start, end);
                return buildSalesReport(orders);
        }

        @Override
        public SalesReportDTO getSalesReport(LocalDate startDate, LocalDate endDate, UUID vendorId) {
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);

                // Get vendor user to check if it's a shared user
                User vendor = userRepository.findById(vendorId)
                                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

                List<Order> orders;
                if (UserUnificationUtil.isSharedUser(vendor.getUsername())) {
                        // Get orders for both shared users
                        List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendor.getUsername());
                        orders = ordenRepository.findByFechaBetween(start, end)
                                        .stream()
                                        .filter(o -> o.getVendedor() != null &&
                                                        sharedUsernames.contains(o.getVendedor().getUsername()))
                                        .toList();
                } else {
                        // Normal vendor - filter by ID
                        orders = ordenRepository.findByFechaBetween(start, end)
                                        .stream()
                                        .filter(o -> o.getVendedor() != null
                                                        && o.getVendedor().getId().equals(vendorId))
                                        .toList();
                }

                return buildSalesReport(orders);
        }
        //rebuild
        private SalesReportDTO buildSalesReport(List<Order> orders) {
                // Calcular métricas básicas
                BigDecimal totalRevenue = orders.stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .map(Order::getTotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                long completedCount = orders.stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .count();

                BigDecimal averageOrderValue = completedCount > 0
                                ? totalRevenue.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;

                int totalOrders = orders.size();
                int completedOrders = (int) completedCount;
                int pendingOrders = (int) orders.stream()
                                .filter(o -> o.getEstado() == OrdenStatus.PENDIENTE
                                                || o.getEstado() == OrdenStatus.CONFIRMADO)
                                .count();
                int canceledOrders = (int) orders.stream()
                                .filter(o -> o.getEstado() == OrdenStatus.CANCELADO)
                                .count();

                // Ventas diarias
                List<DailySalesDTO> dailySales = calculateDailySales(orders);

                // Ventas mensuales
                List<MonthlySalesDTO> monthlySales = calculateMonthlySales(orders);

                return new SalesReportDTO(
                                totalRevenue,
                                averageOrderValue,
                                totalOrders,
                                completedOrders,
                                pendingOrders,
                                canceledOrders,
                                dailySales,
                                monthlySales);
        }

        @Override
        public ProductReportDTO getProductReport() {
                // Producto general: todos los productos
                List<Product> products = productRepository.findAll();
                List<Order> completedOrders = ordenRepository.findByEstado(OrdenStatus.COMPLETADO);
                return buildProductReport(products, completedOrders);
        }

        @Override
        public ProductReportDTO getProductReport(UUID vendorId) {
                List<Product> products = productRepository.findAll();

                // Get vendor user to check if it's a shared user
                User vendor = userRepository.findById(vendorId)
                                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

                List<Order> vendorOrders;
                if (UserUnificationUtil.isSharedUser(vendor.getUsername())) {
                        // Get orders for both shared users
                        List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendor.getUsername());
                        vendorOrders = ordenRepository.findByEstado(OrdenStatus.COMPLETADO)
                                        .stream()
                                        .filter(o -> o.getVendedor() != null &&
                                                        sharedUsernames.contains(o.getVendedor().getUsername()))
                                        .toList();
                } else {
                        vendorOrders = ordenRepository.findByEstado(OrdenStatus.COMPLETADO)
                                        .stream()
                                        .filter(o -> o.getVendedor() != null
                                                        && o.getVendedor().getId().equals(vendorId))
                                        .toList();
                }

                return buildProductReport(products, vendorOrders);
        }

        @Override
        public ProductReportDTO getProductReport(LocalDate startDate, LocalDate endDate) {
                // Productos general filtered by date
                List<Product> products = productRepository.findAll();
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);
                List<Order> completedOrders = ordenRepository.findCompletedOrdersBetween(start, end);
                return buildProductReport(products, completedOrders);
        }

        @Override
        public ProductReportDTO getProductReport(LocalDate startDate, LocalDate endDate, UUID vendorId) {
                List<Product> products = productRepository.findAll();
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);

                // Get vendor user to check if it's a shared user
                User vendor = userRepository.findById(vendorId)
                                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

                List<Order> vendorOrders = ordenRepository.findCompletedOrdersBetween(start, end);

                if (UserUnificationUtil.isSharedUser(vendor.getUsername())) {
                        // Filter for both shared users
                        List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendor.getUsername());
                        vendorOrders = vendorOrders.stream()
                                        .filter(o -> o.getVendedor() != null &&
                                                        sharedUsernames.contains(o.getVendedor().getUsername()))
                                        .toList();
                } else {
                        vendorOrders = vendorOrders.stream()
                                        .filter(o -> o.getVendedor() != null
                                                        && o.getVendedor().getId().equals(vendorId))
                                        .toList();
                }

                return buildProductReport(products, vendorOrders);
        }

        private ProductReportDTO buildProductReport(List<Product> products, List<Order> ordersForStats) {
                int totalProducts = products.size();
                int activeProducts = (int) products.stream().filter(Product::isActive).count();
                int inactiveProducts = totalProducts - activeProducts;
                int lowStockProducts = (int) products.stream()
                                .filter(p -> p.getStock() < 10 && p.isActive())
                                .count();

                // Valor total del inventario
                BigDecimal totalInventoryValue = products.stream()
                                .filter(Product::isActive)
                                .map(p -> p.getPrecio().multiply(BigDecimal.valueOf(p.getStock())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Top productos más vendidos (Basado en las órdenes pasadas)
                List<TopProductDTO> topProducts = calculateTopSellingProducts(ordersForStats);

                // Productos con stock bajo
                List<LowStockProductDTO> lowStockDetails = products.stream()
                                .filter(p -> p.getStock() < 10 && p.isActive())
                                .map(p -> new LowStockProductDTO(
                                                p.getId().toString(),
                                                p.getNombre(),
                                                p.getStock(),
                                                p.getStock() == 0 ? "SIN STOCK" : "STOCK BAJO"))
                                .sorted(Comparator.comparingInt(LowStockProductDTO::currentStock))
                                .limit(10)
                                .toList();

                return new ProductReportDTO(
                                totalProducts,
                                activeProducts,
                                inactiveProducts,
                                lowStockProducts,
                                totalInventoryValue,
                                topProducts,
                                lowStockDetails);
        }

        @Override
        public VendorReportDTO getVendorReport(LocalDate startDate, LocalDate endDate) {
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);

                List<Order> orders = ordenRepository.findByFechaBetween(start, end)
                                .stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .toList();

                // 2. Agrupar por vendedor (unificando usuarios compartidos)
                Map<String, List<Order>> ordersByVendor = orders.stream()
                                .collect(Collectors.groupingBy(o -> {
                                        String username = o.getVendedor().getUsername();
                                        // If this is a shared user, use a unified key
                                        if (UserUnificationUtil.isSharedUser(username)) {
                                                // Use the first shared username as the canonical key
                                                return UserUnificationUtil.getSharedUsernames(username).get(0);
                                        }
                                        return o.getVendedor().getId().toString();
                                }));

                List<VendorPerformanceDTO> topVendors = ordersByVendor.entrySet().stream()
                                .map(entry -> {
                                        List<Order> vendorOrders = entry.getValue();
                                        // Get the actual username from the first order
                                        String actualUsername = vendorOrders.get(0).getVendedor().getUsername();

                                        // If this is a shared user, use the canonical name (NinaTorres)
                                        String vendorName;
                                        if (UserUnificationUtil.isSharedUser(actualUsername)) {
                                                vendorName = UserUnificationUtil.getSharedUsernames(actualUsername)
                                                                .get(0);
                                        } else {
                                                vendorName = actualUsername;
                                        }

                                        int totalOrders = vendorOrders.size();
                                        BigDecimal totalRevenue = vendorOrders.stream()
                                                        .map(Order::getTotal)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        BigDecimal avgOrderValue = totalRevenue.divide(
                                                        BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

                                        return new VendorPerformanceDTO(
                                                        entry.getKey(),
                                                        vendorName,
                                                        totalOrders,
                                                        totalRevenue,
                                                        avgOrderValue);
                                })
                                .sorted(Comparator.comparing(VendorPerformanceDTO::totalRevenue).reversed())
                                .limit(10)
                                .toList();

                int totalVendors = (int) userRepository.count();

                return new VendorReportDTO(totalVendors, topVendors);
        }

        @Override
        public ClientReportDTO getClientReport() {
                List<Client> clients = clientRepository.findAll();
                // Calcular top basado en transacciones globales (o totales históricos del
                // cliente)
                // Para simplificar y consistencia, usamos findAll y lógica en memoria, o
                // queries personalizadas.
                // Aquí usamos la lógica existente.
                return buildClientReport(clients);
        }

        @Override
        public ClientReportDTO getClientReport(UUID vendorId) {
                // Get vendor user to check if it's a shared user
                User vendor = userRepository.findById(vendorId)
                                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

                List<Client> allClients = clientRepository.findAll();
                List<Order> vendorOrders;

                if (UserUnificationUtil.isSharedUser(vendor.getUsername())) {
                        // Get orders for both shared users
                        List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendor.getUsername());
                        vendorOrders = ordenRepository.findAll().stream()
                                        .filter(o -> o.getVendedor() != null &&
                                                        sharedUsernames.contains(o.getVendedor().getUsername()))
                                        .toList();
                } else {
                        vendorOrders = ordenRepository.findAll().stream()
                                        .filter(o -> o.getVendedor() != null
                                                        && o.getVendedor().getId().equals(vendorId))
                                        .toList();
                }

                Set<UUID> vendorClientIds = vendorOrders.stream()
                                .map(o -> o.getCliente().getId())
                                .collect(Collectors.toSet());

                List<Client> vendorClients = allClients.stream()
                                .filter(c -> vendorClientIds.contains(c.getId()))
                                .toList();

                return buildClientReport(vendorClients, vendorOrders);
        }

        @Override
        public ClientReportDTO getClientReport(LocalDate startDate, LocalDate endDate) {
                List<Client> clients = clientRepository.findAll();
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);
                List<Order> ordersInPeriod = ordenRepository.findByFechaBetween(start, end);
                return buildClientReport(clients, ordersInPeriod);
        }

        @Override
        public ClientReportDTO getClientReport(LocalDate startDate, LocalDate endDate, UUID vendorId) {
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);

                // Get vendor user to check if it's a shared user
                User vendor = userRepository.findById(vendorId)
                                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

                List<Client> allClients = clientRepository.findAll();
                List<Order> vendorOrders = ordenRepository.findByFechaBetween(start, end);

                if (UserUnificationUtil.isSharedUser(vendor.getUsername())) {
                        // Filter for both shared users
                        List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendor.getUsername());
                        vendorOrders = vendorOrders.stream()
                                        .filter(o -> o.getVendedor() != null &&
                                                        sharedUsernames.contains(o.getVendedor().getUsername()))
                                        .toList();
                } else {
                        vendorOrders = vendorOrders.stream()
                                        .filter(o -> o.getVendedor() != null
                                                        && o.getVendedor().getId().equals(vendorId))
                                        .toList();
                }

                Set<UUID> vendorClientIds = vendorOrders.stream()
                                .map(o -> o.getCliente().getId())
                                .collect(Collectors.toSet());

                List<Client> vendorClients = allClients.stream()
                                .filter(c -> vendorClientIds.contains(c.getId()))
                                .toList();

                return buildClientReport(vendorClients, vendorOrders);
        }

        private ClientReportDTO buildClientReport(List<Client> clients) {
                return buildClientReport(clients, ordenRepository.findAll());
        }

        private ClientReportDTO buildClientReport(List<Client> clients, List<Order> contextOrders) {
                int totalClients = clients.size();
                int activeClients = (int) clients.stream().filter(Client::isActive).count();

                // Optimización: Map de conteos y totales pre-calculado del contexto
                Map<UUID, Long> orderCounts = contextOrders.stream()
                                .collect(Collectors.groupingBy(o -> o.getCliente().getId(), Collectors.counting()));

                Map<UUID, BigDecimal> orderTotals = contextOrders.stream()
                                .collect(Collectors.groupingBy(
                                                o -> o.getCliente().getId(),
                                                Collectors.mapping(Order::getTotal, Collectors.reducing(BigDecimal.ZERO,
                                                                BigDecimal::add))));

                // Top clientes
                List<TopClientDTO> topClients = clients.stream()
                                .filter(c -> orderTotals.getOrDefault(c.getId(), BigDecimal.ZERO)
                                                .compareTo(BigDecimal.ZERO) > 0)
                                .sorted(Comparator.comparing(
                                                (Client c) -> orderTotals.getOrDefault(c.getId(), BigDecimal.ZERO))
                                                .reversed())
                                .limit(10)
                                .map(c -> {
                                        return new TopClientDTO(
                                                        c.getId().toString(),
                                                        c.getNombre(),
                                                        c.getTelefono(),
                                                        orderTotals.getOrDefault(c.getId(), BigDecimal.ZERO),
                                                        orderCounts.getOrDefault(c.getId(), 0L).intValue());
                                })
                                .toList();

                return new ClientReportDTO(totalClients, activeClients, topClients);
        }

        // ========== MÉTODOS AUXILIARES ==========

        private List<DailySalesDTO> calculateDailySales(List<Order> orders) {
                Map<LocalDate, List<Order>> ordersByDate = orders.stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .collect(Collectors.groupingBy(o -> o.getFecha().toLocalDate()));

                return ordersByDate.entrySet().stream()
                                .map(entry -> {
                                        BigDecimal dailyRevenue = entry.getValue().stream()
                                                        .map(Order::getTotal)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        return new DailySalesDTO(
                                                        entry.getKey(),
                                                        dailyRevenue,
                                                        entry.getValue().size());
                                })
                                .sorted(Comparator.comparing(DailySalesDTO::date))
                                .toList();
        }

        private List<MonthlySalesDTO> calculateMonthlySales(List<Order> orders) {
                Map<String, List<Order>> ordersByMonth = orders.stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .collect(Collectors.groupingBy(o -> o.getFecha().getYear() + "-" +
                                                String.format("%02d", o.getFecha().getMonthValue())));

                return ordersByMonth.entrySet().stream()
                                .map(entry -> {
                                        String[] parts = entry.getKey().split("-");
                                        int year = Integer.parseInt(parts[0]);
                                        int monthNum = Integer.parseInt(parts[1]);
                                        String monthName = java.time.Month.of(monthNum)
                                                        .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));

                                        BigDecimal monthlyRevenue = entry.getValue().stream()
                                                        .map(Order::getTotal)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                        return new MonthlySalesDTO(
                                                        monthName,
                                                        monthNum, // Add numeric month for sorting
                                                        year,
                                                        monthlyRevenue,
                                                        entry.getValue().size());
                                })
                                .sorted(Comparator.comparing(MonthlySalesDTO::year)
                                                .thenComparingInt(MonthlySalesDTO::monthNumber)) // Sort by numeric
                                                                                                 // month
                                .toList();
        }

        private List<TopProductDTO> calculateTopSellingProducts(List<Order> orders) {
                // Usamos las órdenes pasadas como contexto (pueden ser todas o filtradas)
                // Filtrar solo completadas si no vienen ya filtradas?
                // Asumimos que el caller pasa órdenes relevantes (idealmente completadas).
                // Pero `ordersForStats` en `buildProductReport` viene de `completedOrders`.
                // Sin embargo, aseguramos que sean completadas por si acaso, o confiamos en el
                // caller.
                // En `buildProductReport` lines 121 y 134 filtramos por COMPUTADO.
                // Así que iteramos directo.

                Map<String, ProductSalesData> productSales = new HashMap<>();

                orders.forEach(order -> order.getItems().forEach(item -> {
                        String productId = item.getProduct().getId().toString();
                        productSales.computeIfAbsent(productId, k -> new ProductSalesData(
                                        productId,
                                        item.getProduct().getNombre(),
                                        item.getProduct().getImageUrl())).add(item.getCantidad(), item.getSubTotal());
                }));

                return productSales.values().stream()
                                .sorted(Comparator.comparing(ProductSalesData::getQuantitySold).reversed())
                                .limit(10)
                                .map(data -> new TopProductDTO(
                                                data.productId,
                                                data.productName,
                                                data.quantitySold,
                                                data.revenue,
                                                data.imageUrl))
                                .toList();
        }

        // Clase auxiliar para agrupar datos de ventas por producto
        private static class ProductSalesData {
                String productId;
                String productName;
                String imageUrl;
                int quantitySold = 0;
                BigDecimal revenue = BigDecimal.ZERO;

                ProductSalesData(String productId, String productName, String imageUrl) {
                        this.productId = productId;
                        this.productName = productName;
                        this.imageUrl = imageUrl;
                }

                void add(int quantity, BigDecimal amount) {
                        this.quantitySold += quantity;
                        this.revenue = this.revenue.add(amount);
                }

                int getQuantitySold() {
                        return quantitySold;
                }
        }

        @Override
        public List<VendorDailySalesDTO> getVendorDailySalesReport(LocalDate startDate, LocalDate endDate) {
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);

                // 1. Obtener todas las órdenes completadas en el período
                List<Order> completedOrders = ordenRepository.findByFechaBetween(start, end).stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .toList();

                // 2. Agrupar por vendedor (unificando usuarios compartidos)
                Map<String, List<Order>> ordersByVendor = completedOrders.stream()
                                .collect(Collectors.groupingBy(o -> {
                                        String username = o.getVendedor().getUsername();
                                        // If this is a shared user, use a unified key
                                        if (UserUnificationUtil.isSharedUser(username)) {
                                                // Use the first shared username as the canonical key
                                                return UserUnificationUtil.getSharedUsernames(username).get(0);
                                        }
                                        return o.getVendedor().getId().toString();
                                }));

                // 3. Crear reporte por vendedor
                return ordersByVendor.entrySet().stream()
                                .map(vendorEntry -> {
                                        String vendedorId = vendorEntry.getKey();
                                        List<Order> vendorOrders = vendorEntry.getValue();
                                        // Get the actual username from the first order
                                        String actualUsername = vendorOrders.get(0).getVendedor().getUsername();

                                        // If this is a shared user, use the canonical name (NinaTorres)
                                        String vendedorName;
                                        if (UserUnificationUtil.isSharedUser(actualUsername)) {
                                                vendedorName = UserUnificationUtil.getSharedUsernames(actualUsername)
                                                                .get(0);
                                        } else {
                                                vendedorName = actualUsername;
                                        }

                                        // 3.1 Agrupar órdenes por día
                                        Map<LocalDate, List<Order>> ordersByDay = vendorOrders.stream()
                                                        .collect(Collectors
                                                                        .groupingBy(o -> o.getFecha().toLocalDate()));

                                        // 3.2 Crear grupos diarios con agrupación por cliente
                                        List<VendorDailyGroupDTO> dailyGroups = ordersByDay.entrySet().stream()
                                                        .map(dayEntry -> {
                                                                LocalDate dia = dayEntry.getKey();
                                                                List<Order> dayOrders = dayEntry.getValue();

                                                                // Agrupar por cliente dentro del día
                                                                Map<String, List<Order>> ordersByClient = dayOrders
                                                                                .stream()
                                                                                .collect(Collectors.groupingBy(o -> o
                                                                                                .getCliente() != null
                                                                                                                ? o.getCliente().getNombre()
                                                                                                                : "Sin Cliente"));

                                                                // Crear grupos por cliente
                                                                List<ClientDailyGroupDTO> clientGroups = ordersByClient
                                                                                .entrySet().stream()
                                                                                .map(clientEntry -> {
                                                                                        String clientName = clientEntry
                                                                                                        .getKey();
                                                                                        List<Order> clientOrders = clientEntry
                                                                                                        .getValue();

                                                                                        // Crear filas de facturas para
                                                                                        // este cliente
                                                                                        List<VendorInvoiceRowDTO> facturas = clientOrders
                                                                                                        .stream()
                                                                                                        .map(order -> {
                                                                                                                // Determinar
                                                                                                                // número
                                                                                                                // de
                                                                                                                // factura
                                                                                                                // o
                                                                                                                // Remisión
                                                                                                                String numFactura;
                                                                                                                if (order.getInvoiceNumber() != null) {
                                                                                                                        // Verificar
                                                                                                                        // si
                                                                                                                        // es
                                                                                                                        // S/R
                                                                                                                        // (remisión)
                                                                                                                        boolean isSR = order
                                                                                                                                        .getNotas() != null
                                                                                                                                        &&
                                                                                                                                        order.getNotas().contains(
                                                                                                                                                        "[S/R]");
                                                                                                                        numFactura = isSR
                                                                                                                                        ? "Remisión #" + order
                                                                                                                                                        .getInvoiceNumber()
                                                                                                                                        : order.getInvoiceNumber()
                                                                                                                                                        .toString();
                                                                                                                } else {
                                                                                                                        numFactura = "Remisión #"
                                                                                                                                        + order.getId().toString()
                                                                                                                                                        .substring(0, 8);
                                                                                                                }

                                                                                                                // Calcular
                                                                                                                // descuento
                                                                                                                // y
                                                                                                                // pagos
                                                                                                                BigDecimal valorOriginal = order
                                                                                                                                .getTotal();
                                                                                                                BigDecimal discountPercent = order
                                                                                                                                .getDiscountPercentage() != null
                                                                                                                                                ? order.getDiscountPercentage()
                                                                                                                                                : BigDecimal.ZERO;
                                                                                                                BigDecimal valorFinal = order
                                                                                                                                .getDiscountedTotal() != null
                                                                                                                                                ? order.getDiscountedTotal()
                                                                                                                                                : order.getTotal();
                                                                                                                BigDecimal paidAmount = paymentRepository
                                                                                                                                .sumPaymentsByOrderId(
                                                                                                                                                order.getId());
                                                                                                                BigDecimal pendingAmount = valorFinal
                                                                                                                                .subtract(paidAmount);

                                                                                                                return new VendorInvoiceRowDTO(
                                                                                                                                dia,
                                                                                                                                numFactura,
                                                                                                                                clientName,
                                                                                                                                valorOriginal,
                                                                                                                                discountPercent,
                                                                                                                                valorFinal,
                                                                                                                                paidAmount,
                                                                                                                                pendingAmount,
                                                                                                                                order.getPaymentStatus() != null
                                                                                                                                                ? order.getPaymentStatus()
                                                                                                                                                                .name()
                                                                                                                                                : "PENDING",
                                                                                                                                order.getId().toString());
                                                                                                        })
                                                                                                        .sorted(Comparator
                                                                                                                        .comparing(VendorInvoiceRowDTO::numeroFactura))
                                                                                                        .toList();

                                                                                        // Calcular subtotal del cliente
                                                                                        BigDecimal subtotalCliente = facturas
                                                                                                        .stream()
                                                                                                        .map(VendorInvoiceRowDTO::valorFinal)
                                                                                                        .reduce(BigDecimal.ZERO,
                                                                                                                        BigDecimal::add);

                                                                                        return new ClientDailyGroupDTO(
                                                                                                        clientName,
                                                                                                        facturas,
                                                                                                        subtotalCliente);
                                                                                })
                                                                                .sorted(Comparator.comparing(
                                                                                                ClientDailyGroupDTO::clienteNombre))
                                                                                .toList();

                                                                // Calcular total del día
                                                                BigDecimal totalDia = clientGroups.stream()
                                                                                .map(ClientDailyGroupDTO::subtotalCliente)
                                                                                .reduce(BigDecimal.ZERO,
                                                                                                BigDecimal::add);

                                                                return new VendorDailyGroupDTO(dia, clientGroups,
                                                                                totalDia);
                                                        })
                                                        .sorted(Comparator.comparing(VendorDailyGroupDTO::fecha))
                                                        .toList();

                                        // 3.3 Calcular total del período
                                        BigDecimal totalPeriod = dailyGroups.stream()
                                                        .map(VendorDailyGroupDTO::totalDia)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                        return new VendorDailySalesDTO(
                                                        vendedorId,
                                                        vendedorName,
                                                        startDate,
                                                        endDate,
                                                        dailyGroups,
                                                        totalPeriod);
                                })
                                .sorted(Comparator.comparing(VendorDailySalesDTO::vendedorName))
                                .toList();
        }

}
