package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SaleGoalResponse(
        UUID id,
        UUID vendedorId,
        String vendedorUsername,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        BigDecimal percentage,
        int month,
        int year,
        boolean completed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
