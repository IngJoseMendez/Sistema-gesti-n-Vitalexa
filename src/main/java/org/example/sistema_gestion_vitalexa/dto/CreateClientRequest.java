package org.example.sistema_gestion_vitalexa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateClientRequest(
                String nombre, // Opcional - si no se proporciona, se usará el NIT

                @Email(message = "Email inválido") String email, // Opcional

                String telefono, // Opcional

                String direccion, // Opcional

                @NotBlank(message = "El NIT es obligatorio") String nit // ÚNICO CAMPO REQUERIDO - También se usará como
                                                                        // usuario y contraseña

) {

}
