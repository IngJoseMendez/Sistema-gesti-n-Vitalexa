package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para una factura individual de una vendedora
 */
public record VendorInvoiceRowDTO(
        LocalDate fecha,
        String numeroFactura,      // invoiceNumber o id si es null
        String numeroCliente,      // cliente.id
        BigDecimal valor           // order.total
) {}

