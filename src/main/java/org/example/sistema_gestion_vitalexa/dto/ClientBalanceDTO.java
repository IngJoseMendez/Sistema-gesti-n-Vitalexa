package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO con el saldo de un cliente
 */
public record ClientBalanceDTO(
                UUID clientId,
                String clientName,
                String clientPhone,
                String clientRepresentative,
                String vendedorAsignadoName,
                BigDecimal creditLimit, // Tope de crédito
                BigDecimal initialBalance, // Saldo inicial
                BigDecimal totalOrders, // Total de órdenes completadas
                BigDecimal totalPaid, // Total pagado
                BigDecimal pendingBalance, // Saldo pendiente
                BigDecimal balanceFavor, // Saldo a Favor
                Integer pendingOrdersCount, // Cantidad de órdenes pendientes de pago
                List<OrderPendingDTO> pendingOrders // Detalle de órdenes pendientes
) {
}
