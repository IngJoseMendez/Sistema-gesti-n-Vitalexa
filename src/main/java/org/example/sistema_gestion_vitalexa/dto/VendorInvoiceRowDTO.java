package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para una factura individual de una vendedora
 * Incluye información de descuento, estado de pago y saldo pendiente
 */
public record VendorInvoiceRowDTO(
        LocalDate fecha,
        String numeroFactura, // invoiceNumber o "Remisión #ID" si es S/N
        String numeroCliente, // cliente.nombre
        BigDecimal valorOriginal, // order.total (antes de descuento)
        BigDecimal discountPercent, // porcentaje de descuento (0 si no aplica)
        BigDecimal valorFinal, // order.discountedTotal o total
        BigDecimal paidAmount, // total pagado
        BigDecimal pendingAmount, // saldo pendiente
        String paymentStatus, // PENDING, PARTIAL, PAID
        String orderId // ID de la orden (para colorear en Excel)
) {
}
