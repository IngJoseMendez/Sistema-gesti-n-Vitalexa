package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO para una vendedora con sus ventas diarias en un período
 */
public record VendorDailySalesDTO(
        String vendedorId,
        String vendedorName,
        LocalDate startDate,
        LocalDate endDate,
        List<VendorDailyGroupDTO> dailyGroups,
        BigDecimal totalPeriod     // suma de todos los días
) {}
