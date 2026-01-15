package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.OrderItem;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.ProductRepository;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ReportService;
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

    @Override
    public ReportDTO getCompleteReport(LocalDate startDate, LocalDate endDate) {
        return new ReportDTO(
                getSalesReport(startDate, endDate),
                getProductReport(),
                getVendorReport(startDate, endDate),
                getClientReport()
        );
    }

    @Override
    public SalesReportDTO getSalesReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<Order> orders = ordenRepository.findByFechaBetween(start, end);

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
                .filter(o -> o.getEstado() == OrdenStatus.PENDIENTE || o.getEstado() == OrdenStatus.CONFIRMADO)
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
                monthlySales
        );
    }

    @Override
    public ProductReportDTO getProductReport() {
        List<Product> products = productRepository.findAll();

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

        // Top productos más vendidos
        List<TopProductDTO> topProducts = calculateTopSellingProducts();

        // Productos con stock bajo
        List<LowStockProductDTO> lowStockDetails = products.stream()
                .filter(p -> p.getStock() < 10 && p.isActive())
                .map(p -> new LowStockProductDTO(
                        p.getId().toString(),
                        p.getNombre(),
                        p.getStock(),
                        p.getStock() == 0 ? "SIN STOCK" : "STOCK BAJO"
                ))
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
                lowStockDetails
        );
    }

    @Override
    public VendorReportDTO getVendorReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<Order> orders = ordenRepository.findByFechaBetween(start, end)
                .stream()
                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                .toList();

        // Agrupar por vendedor
        Map<String, List<Order>> ordersByVendor = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getVendedor().getId().toString()));

        List<VendorPerformanceDTO> topVendors = ordersByVendor.entrySet().stream()
                .map(entry -> {
                    List<Order> vendorOrders = entry.getValue();
                    String vendorName = vendorOrders.get(0).getVendedor().getUsername();
                    int totalOrders = vendorOrders.size();
                    BigDecimal totalRevenue = vendorOrders.stream()
                            .map(Order::getTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal avgOrderValue = totalRevenue.divide(
                            BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP
                    );

                    return new VendorPerformanceDTO(
                            entry.getKey(),
                            vendorName,
                            totalOrders,
                            totalRevenue,
                            avgOrderValue
                    );
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

        int totalClients = clients.size();
        int activeClients = (int) clients.stream().filter(Client::isActive).count();

        // Top clientes
        List<TopClientDTO> topClients = clients.stream()
                .filter(c -> c.getTotalCompras() != null && c.getTotalCompras().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(Client::getTotalCompras).reversed())
                .limit(10)
                .map(c -> {
                    // Contar órdenes del cliente
                    long orderCount = ordenRepository.findByCliente(c).size();
                    return new TopClientDTO(
                            c.getId().toString(),
                            c.getNombre(),
                            c.getTelefono(),
                            c.getTotalCompras(),
                            (int) orderCount
                    );
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
                            entry.getValue().size()
                    );
                })
                .sorted(Comparator.comparing(DailySalesDTO::date))
                .toList();
    }

    private List<MonthlySalesDTO> calculateMonthlySales(List<Order> orders) {
        Map<String, List<Order>> ordersByMonth = orders.stream()
                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                .collect(Collectors.groupingBy(o ->
                        o.getFecha().getYear() + "-" +
                                String.format("%02d", o.getFecha().getMonthValue())
                ));

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
                            year,
                            monthlyRevenue,
                            entry.getValue().size()
                    );
                })
                .sorted(Comparator.comparing(MonthlySalesDTO::year)
                        .thenComparing(m -> java.time.Month.valueOf(
                                m.month().toUpperCase(Locale.ROOT)).getValue()))
                .toList();
    }

    private List<TopProductDTO> calculateTopSellingProducts() {
        List<Order> completedOrders = ordenRepository.findByEstado(OrdenStatus.COMPLETADO);

        Map<String, ProductSalesData> productSales = new HashMap<>();

        completedOrders.forEach(order ->
                order.getItems().forEach(item -> {
                    String productId = item.getProduct().getId().toString();
                    productSales.computeIfAbsent(productId, k -> new ProductSalesData(
                            productId,
                            item.getProduct().getNombre(),
                            item.getProduct().getImageUrl()
                    )).add(item.getCantidad(), item.getSubTotal());
                })
        );

        return productSales.values().stream()
                .sorted(Comparator.comparing(ProductSalesData::getQuantitySold).reversed())
                .limit(10)
                .map(data -> new TopProductDTO(
                        data.productId,
                        data.productName,
                        data.quantitySold,
                        data.revenue,
                        data.imageUrl
                ))
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

        // 2. Agrupar por vendedor
        Map<String, List<Order>> ordersByVendor = completedOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getVendedor().getId().toString()));

        // 3. Crear reporte por vendedor
        return ordersByVendor.entrySet().stream()
                .map(vendorEntry -> {
                    String vendedorId = vendorEntry.getKey();
                    List<Order> vendorOrders = vendorEntry.getValue();
                    String vendedorName = vendorOrders.get(0).getVendedor().getUsername();

                    // 3.1 Agrupar órdenes por día
                    Map<LocalDate, List<Order>> ordersByDay = vendorOrders.stream()
                            .collect(Collectors.groupingBy(o -> o.getFecha().toLocalDate()));

                    // 3.2 Crear grupos diarios con cálculo de totalDia
                    List<VendorDailyGroupDTO> dailyGroups = ordersByDay.entrySet().stream()
                            .map(dayEntry -> {
                                LocalDate dia = dayEntry.getKey();
                                List<Order> dayOrders = dayEntry.getValue();

                                // Crear filas de facturas para este día
                                List<VendorInvoiceRowDTO> facturas = dayOrders.stream()
                                        .map(order -> new VendorInvoiceRowDTO(
                                                dia,
                                                order.getInvoiceNumber() != null
                                                        ? order.getInvoiceNumber().toString()
                                                        : order.getId().toString().substring(0, 8),
                                                order.getCliente() != null
                                                        ? order.getCliente().getNombre()
                                                        : "N/A",
                                                order.getTotal()
                                        ))
                                        .sorted(Comparator.comparing(VendorInvoiceRowDTO::numeroFactura))
                                        .toList();

                                // Calcular total del día
                                BigDecimal totalDia = dayOrders.stream()
                                        .map(Order::getTotal)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                return new VendorDailyGroupDTO(dia, facturas, totalDia);
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
                            totalPeriod
                    );
                })
                .sorted(Comparator.comparing(VendorDailySalesDTO::vendedorName))
                .toList();
    }

}
