package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

public record VendedorWithGoalResponse(
        UUID id,
        String username,
        String role,
        boolean active,
        SaleGoalResponse currentGoal  // Puede ser null si no tiene meta
) {}
