package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate; /**
 * DTO para agrupar facturas por día (con total del día)
 */
public record VendorDailyGroupDTO(
        LocalDate fecha,
        java.util.List<VendorInvoiceRowDTO> facturas,
        BigDecimal totalDia        // suma de todas las facturas del día
) {}
