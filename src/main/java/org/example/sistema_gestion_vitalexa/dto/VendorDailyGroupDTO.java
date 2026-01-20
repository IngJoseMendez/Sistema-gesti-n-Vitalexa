package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO para agrupar facturas por día y por cliente
 * Estructura: Día -> Cliente -> Facturas -> Subtotal por cliente
 */
public record VendorDailyGroupDTO(
                LocalDate fecha,
                List<ClientDailyGroupDTO> clientGroups, // Grupos por cliente dentro del día
                BigDecimal totalDia // Suma total del día
) {
}
