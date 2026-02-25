package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuesta con la configuración de nómina de un vendedor.
 */
public record VendorPayrollConfigResponse(
        UUID id,
        UUID vendedorId,
        String vendedorUsername,
        BigDecimal baseSalary,
        BigDecimal salesCommissionPct,
        BigDecimal collectionCommissionPct,
        BigDecimal collectionThresholdPct,
        boolean generalCommissionEnabled,
        BigDecimal generalCommissionPct
) {}

