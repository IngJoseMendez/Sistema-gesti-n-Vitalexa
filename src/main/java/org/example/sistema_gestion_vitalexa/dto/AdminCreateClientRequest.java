package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for Admin/Owner creating a client for a specific vendedor
 * (Updated to force recompile)
 */
public record AdminCreateClientRequest(
                @NotNull(message = "El vendedor es obligatorio") UUID vendedorId, // Required - vendedor to assign

                String nombre, // Opcional - nombre del establecimiento

                @Email(message = "Email inválido") String email,

                String telefono,

                String direccion,

                @NotBlank(message = "El NIT es obligatorio") String nit,

                String administrador,

                String representanteLegal) {
}
