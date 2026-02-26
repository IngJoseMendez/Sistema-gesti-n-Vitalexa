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
        /** true = solo cobra comisión de ventas si cumple meta; false = siempre gana % directo */
        boolean salesCommissionByGoal,
        BigDecimal collectionCommissionPct,
        BigDecimal collectionThresholdPct,
        /** true = solo cobra comisión de recaudo si supera umbral; false = siempre gana % directo */
        boolean collectionCommissionByGoal,
        boolean generalCommissionEnabled,
        BigDecimal generalCommissionPct
) {}

