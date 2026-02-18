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
     * Obtiene todos los pagos de una orden (incluyendo anulados)
     */
    List<PaymentResponse> getPaymentsByOrderId(UUID orderId);

    /**
     * Obtiene solo pagos activos de una orden (excluye anulados)
     */
    List<PaymentResponse> getActivePaymentsByOrderId(UUID orderId);

    /**
     * Obtiene un pago específico por ID
     */
    PaymentResponse getPaymentById(UUID paymentId);

    /**
     * Obtiene el total pagado de una orden (solo pagos activos)
     */
    BigDecimal getTotalPaidForOrder(UUID orderId);

    /**
     * Obtiene el saldo pendiente de una orden
     */
    BigDecimal getPendingBalanceForOrder(UUID orderId);

    /**
     * Anula un pago (soft delete con auditoría)
     */
    PaymentResponse cancelPayment(UUID paymentId, String reason, String ownerUsername);

    /**
     * Restaura un pago anulado
     */
    PaymentResponse restorePayment(UUID paymentId, String ownerUsername);

    /**
     * @deprecated Usar cancelPayment en su lugar
     */
    @Deprecated
    void deletePayment(UUID paymentId, String ownerUsername);
}
