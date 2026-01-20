package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.CreatePaymentRequest;
import org.example.sistema_gestion_vitalexa.dto.PaymentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para gestión de pagos/abonos (solo Owner)
 */
public interface PaymentService {

    /**
     * Registra un nuevo pago para una orden
     */
    PaymentResponse registerPayment(CreatePaymentRequest request, String ownerUsername);

    /**
     * Obtiene los pagos de una orden
     */
    List<PaymentResponse> getPaymentsByOrderId(UUID orderId);

    /**
     * Obtiene el total pagado de una orden
     */
    BigDecimal getTotalPaidForOrder(UUID orderId);

    /**
     * Obtiene el saldo pendiente de una orden
     */
    BigDecimal getPendingBalanceForOrder(UUID orderId);

    /**
     * Elimina un pago (anulación)
     */
    void deletePayment(UUID paymentId, String ownerUsername);
}
