package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request para crear o actualizar la configuración de nómina de un vendedor.
 */
public record VendorPayrollConfigRequest(

        @NotNull(message = "El ID del vendedor es obligatorio")
        UUID vendedorId,

        /** Salario base mensual (0 si solo comisiones) */
        @DecimalMin(value = "0.00", message = "El salario base no puede ser negativo")
        BigDecimal baseSalary,

        /** % comisión por ventas en decimal (ej: 0.0150 para 1.5%) */
        @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000")
        BigDecimal salesCommissionPct,

        /** % comisión por recaudo en decimal (ej: 0.0300 para 3%) */
        @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000")
        BigDecimal collectionCommissionPct,

        /** % mínimo de recaudo requerido (ej: 0.8000 para 80%) */
        @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000")
        BigDecimal collectionThresholdPct,

        /** ¿Habilitar comisión general por metas globales? */
        Boolean generalCommissionEnabled,

        /** % comisión general en decimal (ej: 0.0200 para 2%) */
        @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000")
        BigDecimal generalCommissionPct
) {}

