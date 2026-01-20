package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para agrupar facturas por cliente dentro de un día
 */
public record ClientDailyGroupDTO(
        String clienteNombre,
        List<VendorInvoiceRowDTO> facturas,
        BigDecimal subtotalCliente // Suma de facturas del cliente en este día
) {
}
