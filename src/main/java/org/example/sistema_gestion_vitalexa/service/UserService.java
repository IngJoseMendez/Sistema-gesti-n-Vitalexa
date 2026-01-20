package org.example.sistema_gestion_vitalexa.service;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.UserResponse;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.springframework.stereotype.Service;

import java.util.UUID;

public interface UserService {
    User getAuthenticatedUser();

    UserResponse findById(UUID id);

    org.example.sistema_gestion_vitalexa.enums.Role getUserRole(String username);

    /**
     * Crea un usuario para un cliente (nombre=user, nit=pass)
     */
    User registerClientUser(org.example.sistema_gestion_vitalexa.entity.Client client);
}
