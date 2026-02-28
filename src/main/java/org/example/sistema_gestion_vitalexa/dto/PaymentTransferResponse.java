package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response con la información completa de una transferencia de pago.
 */
public record PaymentTransferResponse(
        UUID id,

        // Pago origen
        UUID paymentId,
        BigDecimal paymentTotalAmount,
        UUID orderId,
        String orderClientName,

        // Vendedores
        UUID originVendedorId,
        String originVendedorUsername,
        UUID destVendedorId,
        String destVendedorUsername,

        // Transferencia
        BigDecimal amount,
        Integer targetMonth,
        Integer targetYear,
        String reason,

        // Saldo disponible en el pago (calculado dinámicamente)
        BigDecimal availableAmount,

        // Estado
        Boolean isRevoked,
        LocalDateTime revokedAt,
        String revokedByUsername,
        String revocationReason,

        // Auditoría
        LocalDateTime createdAt,
        String createdByUsername) {
}
