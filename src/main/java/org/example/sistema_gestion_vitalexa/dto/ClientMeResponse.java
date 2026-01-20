package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

public record ClientMeResponse(
        UUID id,
        String nombre,
        String email,
        String telefono,
        String direccion,
        String nit,
        boolean active
) {}
