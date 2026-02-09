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
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.PaymentRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ClientBalanceService;
import org.example.sistema_gestion_vitalexa.util.UserUnificationUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

                // Órdenes pendientes de pago
                List<OrderPendingDTO> pendingOrders = completedOrders.stream()
                                .filter(o -> o.getPaymentStatus() != PaymentStatus.PAID)
                                .map(this::toOrderPendingDTO)
                                .collect(Collectors.toList());

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
                                pendingOrders.size(),
                                pendingOrders);
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
                                order.getFecha(),
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
                                payment.getWithinDeadline(),
                                payment.getDiscountApplied(),
                                payment.getRegisteredBy().getUsername(),
                                payment.getCreatedAt(),
                                payment.getNotes());
        }
}
