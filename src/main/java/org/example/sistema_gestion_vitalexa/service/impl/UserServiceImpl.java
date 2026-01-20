package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.UserResponse;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.UserMapper;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User getAuthenticatedUser() {
        // temporal hasta JWT
        return repository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessExeption("No hay usuario autenticado"));
    }

    @Override
    public UserResponse findById(UUID id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        return userMapper.toResponse(user);
    }

    @Override
    public org.example.sistema_gestion_vitalexa.enums.Role getUserRole(String username) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));
        return user.getRole();
    }

    @Override
    public User registerClientUser(org.example.sistema_gestion_vitalexa.entity.Client client) {
        String username = client.getNombre();

        // Evitar duplicados exactos usando NIT como backup
        if (repository.findByUsername(username).isPresent()) {
            username = username + "_" + client.getNit();
            if (repository.findByUsername(username).isPresent()) {
                throw new BusinessExeption("El usuario '" + username + "' ya existe para este cliente.");
            }
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(client.getNit()))
                .role(org.example.sistema_gestion_vitalexa.enums.Role.CLIENTE)
                .active(true)
                .build();

        return repository.save(user);
    }
}
