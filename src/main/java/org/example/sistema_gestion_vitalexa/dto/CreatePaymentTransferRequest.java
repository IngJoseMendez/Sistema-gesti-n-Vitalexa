package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request para crear una transferencia de pago entre vendedores.
 *
 * @param paymentId      ID del pago activo a transferir
 * @param destVendedorId ID del vendedor que recibirá el crédito de ventas
 * @param amount         Monto a transferir; null = transferir todo el saldo
 *                       disponible
 * @param targetMonth    Mes (1-12) al que se sumará en la nómina del vendedor
 *                       destino
 * @param targetYear     Año al que se sumará en la nómina del vendedor destino
 * @param reason         Motivo de la transferencia (opcional)
 */
public record CreatePaymentTransferRequest(
        UUID paymentId,
        UUID destVendedorId,
        BigDecimal amount,
        Integer targetMonth,
        Integer targetYear,
        String reason) {
}
