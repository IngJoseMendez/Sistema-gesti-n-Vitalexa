package org.example.sistema_gestion_vitalexa.repository;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findBynombre(String name);
    Optional<Client> findByEmail(String email);
    Optional<Client> findByUserUsername(String username);
    boolean existsByUserUsername(String username);

    boolean existsByEmail(@NotBlank(message = "El email es obligatorio") @Email(message = "Email inválido") String email);
}
