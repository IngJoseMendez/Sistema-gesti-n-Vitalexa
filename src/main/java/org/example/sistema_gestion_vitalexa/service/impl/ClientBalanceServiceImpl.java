package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.ClientBalanceDTO;
import org.example.sistema_gestion_vitalexa.dto.OrderPendingDTO;
import org.example.sistema_gestion_vitalexa.dto.PaymentResponse;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.Payment;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.enums.PaymentStatus;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.PaymentRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ClientBalanceService;
import org.example.sistema_gestion_vitalexa.util.UserUnificationUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ClientBalanceServiceImpl implements ClientBalanceService {

        private final ClientRepository clientRepository;
        private final OrdenRepository ordenRepository;
        private final PaymentRepository paymentRepository;
        private final UserRepository userRepository;

        @Override
        public ClientBalanceDTO getClientBalance(UUID clientId) {
                Client client = clientRepository.findById(clientId)
                                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));
                return calculateClientBalance(client);
        }

        @Override
        public List<ClientBalanceDTO> getAllClientBalances() {
                return clientRepository.findAll().stream()
                                .map(this::calculateClientBalance)
                                .collect(Collectors.toList());
        }

        @Override
        public List<ClientBalanceDTO> getClientBalancesByVendedor(UUID vendedorId) {
                // Get vendor to check if it's a shared user
                User vendedor = userRepository.findById(vendedorId)
                                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

                List<Client> clients;
                if (UserUnificationUtil.isSharedUser(vendedor.getUsername())) {
                        // Get clients for both shared usernames
                        List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendedor.getUsername());
                        clients = clientRepository.findByVendedorAsignadoUsernameIn(sharedUsernames);
                } else {
                        clients = clientRepository.findByVendedorAsignadoId(vendedorId);
                }

                return clients.stream()
                                .map(this::calculateClientBalance)
                                .collect(Collectors.toList());
        }

        @Override
        public List<ClientBalanceDTO> getMyClientBalances(String vendedorUsername) {
                User vendedor = userRepository.findByUsername(vendedorUsername)
                                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

                // Check if this is a shared user (NinaTorres/Yicela Sandoval)
                if (UserUnificationUtil.isSharedUser(vendedorUsername)) {
                        // Get clients for both shared usernames
                        List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendedorUsername);
                        return clientRepository.findByVendedorAsignadoUsernameIn(sharedUsernames)
                                        .stream()
                                        .map(this::calculateClientBalance)
                                        .collect(Collectors.toList());
                }

                return getClientBalancesByVendedor(vendedor.getId());
        }

        @Override
        public void setInitialBalance(UUID clientId, BigDecimal initialBalance, String ownerUsername) {
                Client client = clientRepository.findById(clientId)
                                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));

                // Solo se puede establecer una vez
                if (Boolean.TRUE.equals(client.getInitialBalanceSet())) {
                        throw new BusinessExeption("El saldo inicial ya fue establecido y no puede modificarse");
                }

                client.setInitialBalance(initialBalance);
                client.setInitialBalanceSet(true);
                clientRepository.save(client);

                log.info("Saldo inicial de ${} establecido para cliente {} por {}",
                                initialBalance, client.getNombre(), ownerUsername);
        }

        @Override
        public void setCreditLimit(UUID clientId, BigDecimal creditLimit, String ownerUsername) {
                Client client = clientRepository.findById(clientId)
                                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));

                if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessExeption("El tope de crédito debe ser mayor a cero");
                }

                client.setCreditLimit(creditLimit);
                clientRepository.save(client);

                log.info("Tope de crédito de ${} establecido para cliente {} por {}",
                                creditLimit, client.getNombre(), ownerUsername);
        }

        @Override
        public void removeCreditLimit(UUID clientId, String ownerUsername) {
                Client client = clientRepository.findById(clientId)
                                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));

                client.setCreditLimit(null);
                clientRepository.save(client);

                log.info("Tope de crédito eliminado para cliente {} por {}",
                                client.getNombre(), ownerUsername);
        }

        @Override
        public void addBalanceFavor(UUID clientId, BigDecimal amount, String ownerUsername) {
                Client client = clientRepository.findById(clientId)
                                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));

                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessExeption("El monto a agregar debe ser mayor a cero");
                }

                BigDecimal currentBalance = client.getBalanceFavor() != null ? client.getBalanceFavor()
                                : BigDecimal.ZERO;
                client.setBalanceFavor(currentBalance.add(amount));
                clientRepository.save(client);

                log.info("Saldo a favor de ${} agregado a cliente {} por {}. Nuevo saldo: ${}",
                                amount, client.getNombre(), ownerUsername, client.getBalanceFavor());
        }

        /**
         * Calcula el saldo completo de un cliente
         */
        private ClientBalanceDTO calculateClientBalance(Client client) {
                // Obtener órdenes completadas del cliente
                List<Order> completedOrders = ordenRepository.findByCliente(client).stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .collect(Collectors.toList());

                // Calcular totales
                BigDecimal totalOrders = completedOrders.stream()
                                .map(o -> o.getDiscountedTotal() != null ? o.getDiscountedTotal() : o.getTotal())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalPaid = completedOrders.stream()
                                .map(o -> paymentRepository.sumPaymentsByOrderId(o.getId()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal initialBalance = client.getInitialBalance() != null
                                ? client.getInitialBalance()
                                : BigDecimal.ZERO;

                // Saldo pendiente = Total órdenes - Total pagado + Saldo inicial
                BigDecimal pendingBalance = totalOrders.subtract(totalPaid).add(initialBalance);

                // TODAS las órdenes completadas con historial de pagos (no solo pendientes)
                List<OrderPendingDTO> allOrders = completedOrders.stream()
                                .map(this::toOrderPendingDTO)
                                .collect(Collectors.toList());

                // Contar solo las que realmente están pendientes
                int pendingOrdersCount = (int) completedOrders.stream()
                                .filter(o -> o.getPaymentStatus() != PaymentStatus.PAID)
                                .count();

                // Calcular última fecha de pago
                LocalDate lastPaymentDate = getLastPaymentDate(client.getId());

                // Calcular días de mora
                Integer daysOverdue = calculateDaysOverdue(client.getId());

                return new ClientBalanceDTO(
                                client.getId(),
                                client.getNombre(),
                                client.getTelefono(),
                                client.getRepresentanteLegal(),
                                client.getVendedorAsignado() != null
                                                ? client.getVendedorAsignado().getUsername()
                                                : null,
                                client.getCreditLimit(),
                                initialBalance,
                                totalOrders,
                                totalPaid,
                                pendingBalance,
                                client.getBalanceFavor(), // Saldo a favor
                                pendingOrdersCount,
                                allOrders,
                                lastPaymentDate,
                                daysOverdue);
        }

        /**
         * Devuelve la fecha efectiva de la factura:
         * si ya fue completada, usa completedAt; si no (órdenes históricas), usa fecha de creación.
         */
        private java.time.LocalDateTime getInvoiceDate(Order order) {
                return order.getCompletedAt() != null ? order.getCompletedAt() : order.getFecha();
        }

        private OrderPendingDTO toOrderPendingDTO(Order order) {
                BigDecimal orderTotal = order.getDiscountedTotal() != null
                                ? order.getDiscountedTotal()
                                : order.getTotal();
                BigDecimal paidAmount = paymentRepository.sumPaymentsByOrderId(order.getId());
                BigDecimal pendingAmount = orderTotal.subtract(paidAmount);

                List<PaymentResponse> payments = paymentRepository.findByOrderId(order.getId()).stream()
                                .map(this::toPaymentResponse)
                                .collect(Collectors.toList());

                return new OrderPendingDTO(
                                order.getId(),
                                order.getInvoiceNumber(),
                                getInvoiceDate(order),  // ← fecha de completado (o creación como fallback)
                                order.getTotal(),
                                order.getDiscountedTotal(),
                                paidAmount,
                                pendingAmount,
                                order.getPaymentStatus() != null ? order.getPaymentStatus().name()
                                                : PaymentStatus.PENDING.name(),
                                payments);
        }

        private PaymentResponse toPaymentResponse(Payment payment) {
                return new PaymentResponse(
                                payment.getId(),
                                payment.getOrder().getId(),
                                payment.getAmount(),
                                payment.getPaymentDate(),
                                payment.getActualPaymentDate(),
                                payment.getPaymentMethod(),
                                payment.getWithinDeadline(),
                                payment.getDiscountApplied(),
                                payment.getRegisteredBy().getUsername(),
                                payment.getCreatedAt(),
                                payment.getNotes(),
                                payment.getIsCancelled(),
                                payment.getCancelledAt(),
                                payment.getCancelledBy() != null ? payment.getCancelledBy().getUsername() : null,
                                payment.getCancellationReason());
        }

        @Override
        public Integer calculateDaysOverdue(UUID clientId) {
                Client client = clientRepository.findById(clientId)
                                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));

                List<Order> pendingOrders = ordenRepository.findByCliente(client).stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .filter(o -> o.getPaymentStatus() != PaymentStatus.PAID)
                                .toList();

                if (pendingOrders.isEmpty()) {
                        return 0;
                }

                // Obtener la factura más antigua pendiente (usando fecha de completado, no de creación)
                LocalDate oldestInvoiceDate = pendingOrders.stream()
                                .map(o -> getInvoiceDate(o).toLocalDate())
                                .min(LocalDate::compareTo)
                                .orElse(LocalDate.now());

                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(oldestInvoiceDate, LocalDate.now());

                return (int) Math.max(0, daysBetween);
        }

        @Override
        public LocalDate getLastPaymentDate(UUID clientId) {
                List<Payment> payments = paymentRepository.findByClientIdAndNotCancelled(clientId);

                return payments.stream()
                                .map(Payment::getActualPaymentDate)
                                .filter(java.util.Objects::nonNull)
                                .max(LocalDate::compareTo)
                                .orElse(null);
        }

        @Override
        public List<OrderPendingDTO> getPendingInvoicesByClient(UUID clientId, LocalDate startDate,
                        LocalDate endDate) {
                Client client = clientRepository.findById(clientId)
                                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));

                List<Order> completedOrders = ordenRepository.findByCliente(client).stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .filter(o -> o.getPaymentStatus() != PaymentStatus.PAID)
                                .filter(o -> {
                                        LocalDate orderDate = getInvoiceDate(o).toLocalDate();
                                        boolean afterStart = startDate == null || !orderDate.isBefore(startDate);
                                        boolean beforeEnd = endDate == null || !orderDate.isAfter(endDate);
                                        return afterStart && beforeEnd;
                                })
                                .collect(Collectors.toList());

                return completedOrders.stream()
                                .map(this::toOrderPendingDTO)
                                .collect(Collectors.toList());
        }

        @Override
        public List<OrderPendingDTO> getAllInvoicesByClient(UUID clientId, LocalDate startDate, LocalDate endDate) {
                Client client = clientRepository.findById(clientId)
                                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));

                List<Order> completedOrders = ordenRepository.findByCliente(client).stream()
                                .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
                                .filter(o -> {
                                        LocalDate orderDate = getInvoiceDate(o).toLocalDate();
                                        boolean afterStart = startDate == null || !orderDate.isBefore(startDate);
                                        boolean beforeEnd = endDate == null || !orderDate.isAfter(endDate);
                                        return afterStart && beforeEnd;
                                })
                                .sorted((o1, o2) -> getInvoiceDate(o2).compareTo(getInvoiceDate(o1))) // Más recientes primero
                                .collect(Collectors.toList());

                return completedOrders.stream()
                                .map(this::toOrderPendingDTO)
                                .collect(Collectors.toList());
        }

        @Override
        public byte[] exportBalanceToExcel(UUID vendedorId, LocalDate startDate, LocalDate endDate,
                        Boolean onlyWithDebt, String requestingUsername) throws java.io.IOException {

                // Validar permisos
                User user = userRepository.findByUsername(requestingUsername)
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                // Obtener datos según rol
                List<ClientBalanceDTO> balances;
                if (user.getRole() == Role.OWNER || user.getRole() == Role.ADMIN) {
                        if (vendedorId != null) {
                                balances = getClientBalancesByVendedor(vendedorId);
                        } else {
                                balances = getAllClientBalances();
                        }
                } else if (user.getRole() == Role.VENDEDOR) {
                        balances = getMyClientBalances(requestingUsername);
                } else {
                        throw new BusinessExeption("No tienes permisos para exportar cartera");
                }

                // Filtrar por fechas si se especifican
                if (startDate != null || endDate != null) {
                        balances = balances.stream()
                                        .filter(b -> filterByDateRange(b, startDate, endDate))
                                        .toList();
                }

                // Filtrar solo con deuda si se solicita
                if (Boolean.TRUE.equals(onlyWithDebt)) {
                        balances = balances.stream()
                                        .filter(b -> b.pendingBalance().compareTo(BigDecimal.ZERO) > 0)
                                        .toList();
                }

                // Crear Excel con Apache POI
                return createExcelReport(balances);
        }

        private boolean filterByDateRange(ClientBalanceDTO balance, LocalDate startDate, LocalDate endDate) {
                // Filtrar por fecha de la factura más antigua pendiente
                if (balance.pendingOrders() == null || balance.pendingOrders().isEmpty()) {
                        return true;
                }

                LocalDate oldestInvoiceDate = balance.pendingOrders().stream()
                                .map(o -> o.fecha().toLocalDate())
                                .min(LocalDate::compareTo)
                                .orElse(null);

                if (oldestInvoiceDate == null) {
                        return true;
                }

                boolean afterStart = startDate == null || !oldestInvoiceDate.isBefore(startDate);
                boolean beforeEnd = endDate == null || !oldestInvoiceDate.isAfter(endDate);

                return afterStart && beforeEnd;
        }

        private byte[] createExcelReport(List<ClientBalanceDTO> balances) throws java.io.IOException {
                org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                org.apache.poi.ss.usermodel.Sheet sheetDeben = workbook.createSheet("Facturas - Clientes que Deben");
                org.apache.poi.ss.usermodel.Sheet sheetNoDeben = workbook.createSheet("Facturas - Clientes al Día");

                // Separar clientes
                List<ClientBalanceDTO> conDeuda = balances.stream()
                                .filter(b -> b.pendingBalance().compareTo(BigDecimal.ZERO) > 0)
                                .sorted(java.util.Comparator
                                                .comparing((ClientBalanceDTO b) -> b.vendedorAsignadoName() != null
                                                                ? b.vendedorAsignadoName()
                                                                : "")
                                                .thenComparing(ClientBalanceDTO::clientName))
                                .toList();

                List<ClientBalanceDTO> sinDeuda = balances.stream()
                                .filter(b -> b.pendingBalance().compareTo(BigDecimal.ZERO) <= 0)
                                .sorted(java.util.Comparator
                                                .comparing((ClientBalanceDTO b) -> b.vendedorAsignadoName() != null
                                                                ? b.vendedorAsignadoName()
                                                                : "")
                                                .thenComparing(ClientBalanceDTO::clientName))
                                .toList();

                // Crear headers
                createDetailedHeaderRow(sheetDeben, workbook);
                createDetailedHeaderRow(sheetNoDeben, workbook);

                // Llenar datos detallados por factura
                fillDetailedInvoiceSheet(sheetDeben, conDeuda, workbook);
                fillDetailedInvoiceSheet(sheetNoDeben, sinDeuda, workbook);

                // Auto-size columns
                for (int i = 0; i < 10; i++) {
                        sheetDeben.autoSizeColumn(i);
                        sheetNoDeben.autoSizeColumn(i);
                }

                // Convertir a bytes
                java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                workbook.write(outputStream);
                workbook.close();

                return outputStream.toByteArray();
        }

        private void createDetailedHeaderRow(org.apache.poi.ss.usermodel.Sheet sheet,
                        org.apache.poi.ss.usermodel.Workbook workbook) {
                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                String[] headers = {
                                "Vendedor", "Cliente", "Factura #",
                                "Fecha Despacho", "Total Factura",
                                "Monto Pagado", "Saldo Pendiente", "Estado Pago",
                                "Fecha Pago", "Monto Pago"
                };

                org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
                org.apache.poi.ss.usermodel.Font font = workbook.createFont();
                font.setBold(true);
                font.setFontHeightInPoints((short) 10);
                headerStyle.setFont(font);
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.getIndex());
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

                // Texto blanco para el header
                font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
                headerStyle.setFont(font);

                // Bordes
                headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

                for (int i = 0; i < headers.length; i++) {
                        org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                        cell.setCellValue(headers[i]);
                        cell.setCellStyle(headerStyle);

                        // Ajustar ancho de columnas específicas
                        switch (i) {
                                case 0: // Vendedor
                                        sheet.setColumnWidth(i, 3500);
                                        break;
                                case 1: // Cliente
                                        sheet.setColumnWidth(i, 5000);
                                        break;
                                case 3, 8: // Fechas
                                        sheet.setColumnWidth(i, 3000);
                                        break;
                                case 4, 5, 6, 9: // Montos
                                        sheet.setColumnWidth(i, 3500);
                                        break;
                        }
                }
        }

        private void fillDetailedInvoiceSheet(org.apache.poi.ss.usermodel.Sheet sheet, List<ClientBalanceDTO> balances,
                        org.apache.poi.ss.usermodel.Workbook workbook) {
                int rowNum = 1;

                // Estilos
                org.apache.poi.ss.usermodel.CellStyle currencyStyle = workbook.createCellStyle();
                org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
                currencyStyle.setDataFormat(format.getFormat("$#,##0"));

                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(format.getFormat("dd/mm/yyyy"));

                org.apache.poi.ss.usermodel.CellStyle paidStyle = workbook.createCellStyle();
                paidStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_GREEN.getIndex());
                paidStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

                org.apache.poi.ss.usermodel.CellStyle pendingStyle = workbook.createCellStyle();
                pendingStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_YELLOW.getIndex());
                pendingStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

                org.apache.poi.ss.usermodel.CellStyle overdueStyle = workbook.createCellStyle();
                overdueStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.CORAL.getIndex());
                overdueStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

                for (ClientBalanceDTO balance : balances) {
                        String vendedorName = balance.vendedorAsignadoName() != null ? balance.vendedorAsignadoName()
                                        : "Sin asignar";
                        String clientName = balance.clientName();

                        // Procesar todas las facturas del cliente
                        List<OrderPendingDTO> allInvoices = balance.pendingOrders();
                        if (allInvoices == null || allInvoices.isEmpty()) {
                                // Cliente sin facturas - crear una fila resumen
                                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                                row.createCell(0).setCellValue(vendedorName);
                                row.createCell(1).setCellValue(clientName);
                                row.createCell(2).setCellValue("Sin facturas");
                                row.createCell(3).setCellValue("");

                                org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(4);
                                totalCell.setCellValue(0);
                                totalCell.setCellStyle(currencyStyle);

                                org.apache.poi.ss.usermodel.Cell paidCell = row.createCell(5);
                                paidCell.setCellValue(0);
                                paidCell.setCellStyle(currencyStyle);

                                org.apache.poi.ss.usermodel.Cell pendingCell = row.createCell(6);
                                pendingCell.setCellValue(0);
                                pendingCell.setCellStyle(currencyStyle);

                                row.createCell(7).setCellValue("N/A");
                                row.createCell(8).setCellValue("");
                                row.createCell(9).setCellValue("");
                                continue;
                        }

                        // Ordenar facturas por fecha (más recientes primero)
                        List<OrderPendingDTO> sortedInvoices = allInvoices.stream()
                                        .sorted((a, b) -> b.fecha().compareTo(a.fecha()))
                                        .toList();

                        for (OrderPendingDTO invoice : sortedInvoices) {
                                // Obtener pagos activos directamente del repositorio (más fiable que el DTO)
                                List<Payment> activePaymentEntities = paymentRepository
                                                .findActivePaymentsByOrderId(invoice.orderId());

                                // Si no tiene pagos, mostrar una fila con la info de la factura
                                int rowsToCreate = activePaymentEntities.isEmpty() ? 1 : activePaymentEntities.size();

                                for (int payIdx = 0; payIdx < rowsToCreate; payIdx++) {
                                        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);

                                        // Columnas comunes de factura (siempre se muestran)
                                        row.createCell(0).setCellValue(vendedorName);
                                        row.createCell(1).setCellValue(clientName);

                                        // Info de factura solo en la primera fila del grupo
                                        if (payIdx == 0) {
                                                row.createCell(2)
                                                                .setCellValue(invoice.invoiceNumber() != null
                                                                                ? invoice.invoiceNumber().toString()
                                                                                : "N/A");

                                                // Fecha de despacho (completedAt, ya resuelto en el DTO)
                                                String dispatchDate = invoice.fecha() != null
                                                                ? invoice.fecha().toLocalDate().toString()
                                                                : "Sin fecha";
                                                row.createCell(3).setCellValue(dispatchDate);

                                                // Montos de la factura
                                                BigDecimal invoiceTotal = invoice.discountedTotal() != null
                                                                ? invoice.discountedTotal()
                                                                : invoice.total();
                                                org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(4);
                                                totalCell.setCellValue(invoiceTotal.doubleValue());
                                                totalCell.setCellStyle(currencyStyle);

                                                org.apache.poi.ss.usermodel.Cell paidCell = row.createCell(5);
                                                paidCell.setCellValue(invoice.paidAmount().doubleValue());
                                                paidCell.setCellStyle(currencyStyle);

                                                org.apache.poi.ss.usermodel.Cell pendingCell = row.createCell(6);
                                                pendingCell.setCellValue(invoice.pendingAmount().doubleValue());
                                                pendingCell.setCellStyle(currencyStyle);

                                                // Estado de pago - recalcular basado en datos reales
                                                org.apache.poi.ss.usermodel.Cell statusCell = row.createCell(7);
                                                String realStatus;
                                                if (invoice.pendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                                                        realStatus = "Pagado";
                                                        statusCell.setCellStyle(paidStyle);
                                                } else if (invoice.paidAmount().compareTo(BigDecimal.ZERO) > 0) {
                                                        realStatus = "Parcial";
                                                        statusCell.setCellStyle(overdueStyle);
                                                } else {
                                                        realStatus = "Pendiente";
                                                        statusCell.setCellStyle(pendingStyle);
                                                }
                                                statusCell.setCellValue(realStatus);
                                        }

                                        // Columnas de pago individual
                                        if (!activePaymentEntities.isEmpty()) {
                                                Payment payment = activePaymentEntities.get(payIdx);

                                                // Fecha del pago (usar actualPaymentDate, si no paymentDate)
                                                org.apache.poi.ss.usermodel.Cell payDateCell = row.createCell(8);
                                                if (payment.getActualPaymentDate() != null) {
                                                        payDateCell.setCellValue(
                                                                        payment.getActualPaymentDate().toString());
                                                } else if (payment.getPaymentDate() != null) {
                                                        payDateCell.setCellValue(
                                                                        payment.getPaymentDate().toLocalDate()
                                                                                        .toString());
                                                } else {
                                                        payDateCell.setCellValue("Sin fecha");
                                                }

                                                // Monto del pago
                                                org.apache.poi.ss.usermodel.Cell payAmountCell = row.createCell(9);
                                                payAmountCell.setCellValue(payment.getAmount().doubleValue());
                                                payAmountCell.setCellStyle(currencyStyle);
                                        } else {
                                                row.createCell(8).setCellValue("Sin pagos");
                                                org.apache.poi.ss.usermodel.Cell payAmountCell = row.createCell(9);
                                                payAmountCell.setCellValue(0);
                                                payAmountCell.setCellStyle(currencyStyle);
                                        }
                                }
                        }
                }
        }


        private String getPaymentStatusText(String status) {
                return switch (status) {
                        case "PAID" -> "Pagado";
                        case "PARTIAL" -> "Parcial";
                        case "PENDING" -> "Pendiente";
                        default -> status;
                };
        }

        private String getLastPaymentDateForInvoice(List<PaymentResponse> payments) {
                if (payments == null || payments.isEmpty()) {
                        return "Sin pagos";
                }

                return payments.stream()
                                .filter(p -> !Boolean.TRUE.equals(p.isCancelled()))
                                .map(PaymentResponse::actualPaymentDate)
                                .filter(java.util.Objects::nonNull)
                                .max(LocalDate::compareTo)
                                .map(LocalDate::toString)
                                .orElse("Sin pagos");
        }

        private int calculateDaysSinceCreation(java.time.LocalDateTime fechaCreacion) {
                if (fechaCreacion == null) {
                        return 0;
                }
                return (int) java.time.temporal.ChronoUnit.DAYS.between(fechaCreacion.toLocalDate(), LocalDate.now());
        }

}
