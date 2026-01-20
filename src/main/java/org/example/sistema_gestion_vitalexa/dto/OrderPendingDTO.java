package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO con información de una orden pendiente de pago
 */
public record OrderPendingDTO(
        UUID orderId,
        Long invoiceNumber,
        LocalDateTime fecha,
        BigDecimal total,
        BigDecimal discountedTotal,
        BigDecimal paidAmount,
        BigDecimal pendingAmount,
        String paymentStatus,
        List<PaymentResponse> payments) {
}
