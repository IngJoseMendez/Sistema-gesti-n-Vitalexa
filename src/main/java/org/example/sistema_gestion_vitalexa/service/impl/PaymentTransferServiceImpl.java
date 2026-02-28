package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.CreatePaymentTransferRequest;
import org.example.sistema_gestion_vitalexa.dto.PaymentTransferResponse;
import org.example.sistema_gestion_vitalexa.dto.RevokePaymentTransferRequest;
import org.example.sistema_gestion_vitalexa.entity.Payment;
import org.example.sistema_gestion_vitalexa.entity.PaymentTransfer;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.PaymentRepository;
import org.example.sistema_gestion_vitalexa.repository.PaymentTransferRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.PaymentTransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentTransferServiceImpl implements PaymentTransferService {

    private final PaymentTransferRepository transferRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // CREAR TRANSFERENCIA
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public PaymentTransferResponse createTransfer(CreatePaymentTransferRequest request, String ownerUsername) {

        User owner = findUser(ownerUsername);

        // 1. Cargar el pago y validar que esté activo
        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new BusinessExeption("Pago no encontrado"));

        if (Boolean.TRUE.equals(payment.getIsCancelled())) {
            throw new BusinessExeption("No se puede transferir un pago anulado");
        }

        // 2. Calcular saldo disponible
        BigDecimal alreadyTransferred = transferRepository.sumActiveTransfersByPaymentId(payment.getId());
        BigDecimal available = payment.getAmount().subtract(alreadyTransferred);

        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessExeption("El pago no tiene saldo disponible para transferir");
        }

        // 3. Determinar el monto a transferir (null = todo el disponible)
        BigDecimal amountToTransfer = request.amount() != null ? request.amount() : available;

        if (amountToTransfer.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessExeption("El monto a transferir debe ser mayor que cero");
        }
        if (amountToTransfer.compareTo(available) > 0) {
            throw new BusinessExeption(
                    "El monto a transferir ($" + amountToTransfer +
                            ") supera el saldo disponible ($" + available + ")");
        }

        // 4. Validar vendedor destino
        User destVendedor = userRepository.findById(request.destVendedorId())
                .orElseThrow(() -> new BusinessExeption("Vendedor destino no encontrado"));

        User originVendedor = payment.getOrder().getVendedor();

        if (originVendedor.getId().equals(destVendedor.getId())) {
            throw new BusinessExeption("El vendedor destino debe ser distinto al vendedor origen");
        }

        // 5. Validar mes/año
        if (request.targetMonth() == null || request.targetYear() == null) {
            throw new BusinessExeption("Debe especificar el mes y año destino");
        }
        if (request.targetMonth() < 1 || request.targetMonth() > 12) {
            throw new BusinessExeption("El mes debe estar entre 1 y 12");
        }
        if (request.targetYear() < 2020) {
            throw new BusinessExeption("El año debe ser 2020 o posterior");
        }

        // 6. Crear la transferencia
        PaymentTransfer transfer = PaymentTransfer.builder()
                .payment(payment)
                .originVendedor(originVendedor)
                .destVendedor(destVendedor)
                .amount(amountToTransfer)
                .targetMonth(request.targetMonth())
                .targetYear(request.targetYear())
                .reason(request.reason())
                .isRevoked(false)
                .createdBy(owner)
                .build();

        PaymentTransfer saved = transferRepository.save(transfer);

        log.info("Transferencia creada: ${} de pago {} (vendedor {}) → vendedor {} para {}/{}",
                amountToTransfer, payment.getId(),
                originVendedor.getUsername(), destVendedor.getUsername(),
                request.targetMonth(), request.targetYear());

        return toResponse(saved, available.subtract(amountToTransfer));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REVOCAR TRANSFERENCIA
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public PaymentTransferResponse revokeTransfer(UUID transferId, RevokePaymentTransferRequest request,
            String ownerUsername) {

        User owner = findUser(ownerUsername);

        PaymentTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessExeption("Transferencia no encontrada"));

        if (Boolean.TRUE.equals(transfer.getIsRevoked())) {
            throw new BusinessExeption("La transferencia ya está revocada");
        }

        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessExeption("Debe indicar el motivo de la revocación");
        }

        transfer.setIsRevoked(true);
        transfer.setRevokedAt(LocalDateTime.now());
        transfer.setRevokedBy(owner);
        transfer.setRevocationReason(request.reason());

        PaymentTransfer saved = transferRepository.save(transfer);

        // Saldo disponible actualizado (se devuelve el monto revocado)
        BigDecimal newAvailable = transferRepository.sumActiveTransfersByPaymentId(transfer.getPayment().getId());
        BigDecimal available = transfer.getPayment().getAmount().subtract(newAvailable);

        log.info("Transferencia {} revocada por {} — Razón: {}", transferId, ownerUsername, request.reason());

        return toResponse(saved, available);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTAS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransferResponse> getTransfersByPayment(UUID paymentId) {
        return transferRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId)
                .stream()
                .map(t -> {
                    BigDecimal activeTransferred = transferRepository.sumActiveTransfersByPaymentId(paymentId);
                    BigDecimal available = t.getPayment().getAmount().subtract(activeTransferred);
                    return toResponse(t, available);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransferResponse> getTransfersByOriginVendedor(UUID vendedorId) {
        return transferRepository.findByOriginVendedorIdOrderByCreatedAtDesc(vendedorId)
                .stream()
                .map(t -> {
                    BigDecimal activeTransferred = transferRepository
                            .sumActiveTransfersByPaymentId(t.getPayment().getId());
                    BigDecimal available = t.getPayment().getAmount().subtract(activeTransferred);
                    return toResponse(t, available);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransferResponse> getTransfersByDestVendedor(UUID vendedorId) {
        return transferRepository.findByDestVendedorIdOrderByCreatedAtDesc(vendedorId)
                .stream()
                .map(t -> {
                    BigDecimal activeTransferred = transferRepository
                            .sumActiveTransfersByPaymentId(t.getPayment().getId());
                    BigDecimal available = t.getPayment().getAmount().subtract(activeTransferred);
                    return toResponse(t, available);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAvailableAmountForPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessExeption("Pago no encontrado"));

        BigDecimal alreadyTransferred = transferRepository.sumActiveTransfersByPaymentId(paymentId);
        return payment.getAmount().subtract(alreadyTransferred);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado: " + username));
    }

    private PaymentTransferResponse toResponse(PaymentTransfer t, BigDecimal availableAmount) {
        Payment payment = t.getPayment();
        String clientName = payment.getOrder().getCliente() != null
                ? payment.getOrder().getCliente().getNombre()
                : "(sin cliente)";

        return new PaymentTransferResponse(
                t.getId(),
                payment.getId(),
                payment.getAmount(),
                payment.getOrder().getId(),
                clientName,
                t.getOriginVendedor().getId(),
                t.getOriginVendedor().getUsername(),
                t.getDestVendedor().getId(),
                t.getDestVendedor().getUsername(),
                t.getAmount(),
                t.getTargetMonth(),
                t.getTargetYear(),
                t.getReason(),
                availableAmount,
                t.getIsRevoked(),
                t.getRevokedAt(),
                t.getRevokedBy() != null ? t.getRevokedBy().getUsername() : null,
                t.getRevocationReason(),
                t.getCreatedAt(),
                t.getCreatedBy().getUsername());
    }
}
