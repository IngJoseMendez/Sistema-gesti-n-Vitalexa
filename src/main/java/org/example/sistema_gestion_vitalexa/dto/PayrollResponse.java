package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Respuesta con el detalle completo de la nómina mensual de un vendedor.
 */
public record PayrollResponse(

        UUID id,
        UUID vendedorId,
        String vendedorUsername,
        int month,
        int year,

        // ─── Salario Base ──────────────────────────────────
        BigDecimal baseSalary,

        // ─── Comisión por Meta de Ventas ───────────────────
        BigDecimal salesGoalTarget,
        BigDecimal totalSold,
        boolean salesGoalMet,
        BigDecimal salesCommissionPct,
        BigDecimal salesCommissionAmount,

        // ─── Comisión por Meta de Recaudo ──────────────────
        BigDecimal prevMonthTotalSold,
        BigDecimal totalCollected,
        BigDecimal collectionPct,
        boolean collectionGoalMet,
        BigDecimal collectionCommissionPct,
        BigDecimal collectionCommissionAmount,

        // ─── Comisión General ──────────────────────────────
        boolean generalCommissionEnabled,
        BigDecimal totalGlobalGoals,
        BigDecimal generalCommissionPct,
        BigDecimal generalCommissionAmount,

        // ─── Totales ───────────────────────────────────────
        BigDecimal totalCommissions,
        BigDecimal totalPayout,

        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

