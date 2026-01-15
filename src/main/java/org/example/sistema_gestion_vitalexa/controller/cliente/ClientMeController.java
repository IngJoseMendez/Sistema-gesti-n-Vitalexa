package org.example.sistema_gestion_vitalexa.controller.cliente;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ClientMeResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateClientMeRequest;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/cliente/me")
@RequiredArgsConstructor

@PreAuthorize("hasRole('CLIENTE')")
public class ClientMeController {

    private final ClientRepository clientRepository;

    @GetMapping
    public ClientMeResponse me(Principal principal) {
        Client client = clientRepository.findByUserUsername(principal.getName())
                .orElseThrow(() -> new BusinessExeption("Client no asociado"));
        return new ClientMeResponse(
                client.getId(), client.getNombre(), client.getEmail(),
                client.getTelefono(), client.getDireccion(), client.isActive()
        );
    }

    @PatchMapping
    @Transactional
    public ClientMeResponse update(@RequestBody UpdateClientMeRequest request, Principal principal) {
        Client client = clientRepository.findByUserUsername(principal.getName())
                .orElseThrow(() -> new BusinessExeption("Client no asociado"));

        if (request.email() != null) client.setEmail(request.email());
        if (request.telefono() != null) client.setTelefono(request.telefono());
        if (request.direccion() != null) client.setDireccion(request.direccion());

        clientRepository.save(client);

        return new ClientMeResponse(
                client.getId(), client.getNombre(), client.getEmail(),
                client.getTelefono(), client.getDireccion(), client.isActive()
        );
    }
}

