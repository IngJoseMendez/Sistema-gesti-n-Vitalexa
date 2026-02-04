package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;

public record MonthlySalesDTO(
                String month,
                Integer monthNumber, // Numeric month value (1-12) for proper sorting
                Integer year,
                BigDecimal revenue,
                Integer orders) {
}
