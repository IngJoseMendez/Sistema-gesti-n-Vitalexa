package org.example.sistema_gestion_vitalexa.dto;

import java.util.List;

public record CustomerOrderRequestDto(
        List<OrderItemRequestDTO> items,
        String notas
) {}

