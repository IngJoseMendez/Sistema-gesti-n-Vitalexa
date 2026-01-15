package org.example.sistema_gestion_vitalexa.dto;

public record UpdateClientMeRequest(
        String email,
        String telefono,
        String direccion
) {}
