package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateClientRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "El teléfono es obligatorio")
        String telefono,

        String direccion,
        @NotBlank(message = "El NIT es obligatorio")
        String nit

) {

}
