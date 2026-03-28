package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO optimizado para el agente Vicky (WhatsApp).
 * Consolida en una sola respuesta: balance, mora, último pago y conteo de facturas.
 * Permite responder en 1 llamada en lugar de 3.
 *
 * Endpoint: GET /api/balances/summary?search=NOMBRE
 */
public record ClientDebtSummaryDTO(
        UUID clientId,
        String clientName,
        String clientPhone,
        String vendedorName,
        BigDecimal totalDebt,        // Saldo pendiente total
        BigDecimal creditLimit,      // Tope de crédito (null si no tiene)
        BigDecimal balanceFavor,     // Saldo a favor
        Integer daysOverdue,         // Días de mora
        LocalDate lastPaymentDate,   // Última fecha de pago
        Integer pendingInvoicesCount // Cantidad de facturas sin saldar
) {
}
