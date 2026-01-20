package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.CreatePaymentRequest;
import org.example.sistema_gestion_vitalexa.dto.PaymentResponse;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.Payment;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.enums.PaymentStatus;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.PaymentRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.PaymentService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrdenRepository ordenRepository;
    private final UserRepository userRepository;

    @Override
    public PaymentResponse registerPayment(CreatePaymentRequest request, String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        Order order = ordenRepository.findById(request.orderId())
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        // Validar que la orden esté completada
        if (order.getEstado() != OrdenStatus.COMPLETADO) {
            throw new BusinessExeption("Solo se pueden registrar pagos en órdenes completadas");
        }

        // Calcular saldo pendiente
        BigDecimal orderTotal = order.getDiscountedTotal() != null
                ? order.getDiscountedTotal()
                : order.getTotal();
        BigDecimal totalPaid = getTotalPaidForOrder(order.getId());
        BigDecimal pendingBalance = orderTotal.subtract(totalPaid);

        // Validar que el monto no exceda el saldo pendiente
        if (request.amount().compareTo(pendingBalance) > 0) {
            throw new BusinessExeption("El monto del pago ($" + request.amount() +
                    ") excede el saldo pendiente ($" + pendingBalance + ")");
        }

        // Crear el pago
        Payment payment = Payment.builder()
                .order(order)
                .amount(request.amount())
                .paymentDate(LocalDateTime.now())
                .withinDeadline(request.withinDeadline() != null ? request.withinDeadline() : false)
                .discountApplied(request.discountApplied() != null ? request.discountApplied() : BigDecimal.ZERO)
                .registeredBy(owner)
                .notes(request.notes())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Actualizar estado de pago de la orden
        updateOrderPaymentStatus(order);

        log.info("Pago registrado: ${} para orden {} por {}",
                request.amount(), order.getId(), ownerUsername);

        return toPaymentResponse(savedPayment);
    }

    @Override
    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId) {
        return paymentRepository.findByOrderIdOrderByPaymentDateDesc(orderId)
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Override
    public BigDecimal getTotalPaidForOrder(UUID orderId) {
        return paymentRepository.sumPaymentsByOrderId(orderId);
    }

    @Override
    public BigDecimal getPendingBalanceForOrder(UUID orderId) {
        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        BigDecimal orderTotal = order.getDiscountedTotal() != null
                ? order.getDiscountedTotal()
                : order.getTotal();
        BigDecimal totalPaid = getTotalPaidForOrder(orderId);

        return orderTotal.subtract(totalPaid);
    }

    @Override
    public void deletePayment(UUID paymentId, String ownerUsername) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessExeption("Pago no encontrado"));

        Order order = payment.getOrder();
        paymentRepository.delete(payment);

        // Actualizar estado de pago de la orden
        updateOrderPaymentStatus(order);

        log.info("Pago {} anulado por {}", paymentId, ownerUsername);
    }

    /**
     * Actualiza el estado de pago de la orden basado en los pagos realizados
     */
    private void updateOrderPaymentStatus(Order order) {
        BigDecimal orderTotal = order.getDiscountedTotal() != null
                ? order.getDiscountedTotal()
                : order.getTotal();
        BigDecimal totalPaid = getTotalPaidForOrder(order.getId());

        PaymentStatus newStatus;
        if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = PaymentStatus.PENDING;
        } else if (totalPaid.compareTo(orderTotal) >= 0) {
            newStatus = PaymentStatus.PAID;
        } else {
            newStatus = PaymentStatus.PARTIAL;
        }

        order.setPaymentStatus(newStatus);
        ordenRepository.save(order);
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
