package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.CreatePaymentTransferRequest;
import org.example.sistema_gestion_vitalexa.dto.PaymentTransferResponse;
import org.example.sistema_gestion_vitalexa.dto.RevokePaymentTransferRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para gestión de transferencias de pagos entre vendedores.
 * Solo el Owner puede crear y revocar transferencias.
 */
public interface PaymentTransferService {

    /**
     * Crea una transferencia del valor de un pago hacia otro vendedor
     * en un mes/año específico para efectos de nómina.
     */
    PaymentTransferResponse createTransfer(CreatePaymentTransferRequest request, String ownerUsername);

    /**
     * Revoca una transferencia existente (soft delete con auditoría).
     * El saldo vuelve al pago origen y se descuenta del vendedor destino
     * en la próxima recalculación de nómina.
     */
    PaymentTransferResponse revokeTransfer(UUID transferId, RevokePaymentTransferRequest request, String ownerUsername);

    /**
     * Lista todas las transferencias realizadas sobre un pago específico.
     */
    List<PaymentTransferResponse> getTransfersByPayment(UUID paymentId);

    /**
     * Lista las transferencias donde el vendedor es el ORIGEN.
     */
    List<PaymentTransferResponse> getTransfersByOriginVendedor(UUID vendedorId);

    /**
     * Lista las transferencias donde el vendedor es el DESTINO.
     */
    List<PaymentTransferResponse> getTransfersByDestVendedor(UUID vendedorId);

    /**
     * Calcula el saldo disponible para transferir de un pago:
     * saldo = pago.amount - suma_transferencias_activas_sobre_ese_pago
     */
    BigDecimal getAvailableAmountForPayment(UUID paymentId);
}
