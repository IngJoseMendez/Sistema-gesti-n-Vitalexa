package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record UpdateSaleGoalRequest(
        @DecimalMin(value = "0.01", message = "La meta debe ser mayor a 0")
        BigDecimal targetAmount
) {}
