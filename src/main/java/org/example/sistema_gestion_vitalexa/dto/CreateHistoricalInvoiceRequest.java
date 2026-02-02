package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.sistema_gestion_vitalexa.enums.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO para crear facturas históricas (cuadre de caja)
 * Solo para Owner - sin productos, solo para registro
 *
 * IMPORTANTE:
 * - totalValue: Monto total de la factura
 * - amountPaid: Cuánto pagó el cliente (el sistema calcula automáticamente lo que debe)
 * - Si amountPaid = totalValue → Factura completamente pagada
 * - Si amountPaid < totalValue → Cliente debe la diferencia
 * - Si amountPaid = 0 → Cliente debe la factura completa
 */
public record CreateHistoricalInvoiceRequest(
        @NotNull(message = "El número de factura es obligatorio")
        @Positive(message = "El número de factura debe ser positivo")
        Long invoiceNumber,

        @NotNull(message = "La fecha es obligatoria")
        LocalDateTime fecha,

        @Positive(message = "El valor de la factura debe ser positivo")
        @NotNull(message = "El valor de la factura es obligatorio")
        BigDecimal totalValue,

        @NotNull(message = "El monto pagado es obligatorio")
        @org.hibernate.validator.constraints.Range(min = 0, message = "El monto pagado debe ser 0 o mayor")
        BigDecimal amountPaid,

        // Tipo de factura: NORMAL, SR o PROMO
        @NotNull(message = "El tipo de factura es obligatorio (NORMAL, SR o PROMO)")
        InvoiceType invoiceType,

        // Cliente: Preferentemente usar ID si está registrado en el sistema
        UUID clientId,

        // Datos opcionales de cliente (solo si NO está registrado o para referencia adicional)
        String clientName,
        String clientPhone,
        String clientEmail,
        String clientAddress,

        // Notas opcionales
        String notes
) {
}

