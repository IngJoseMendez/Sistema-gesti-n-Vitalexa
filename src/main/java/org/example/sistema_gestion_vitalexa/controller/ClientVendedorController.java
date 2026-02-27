package org.example.sistema_gestion_vitalexa.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateClientRequest;
import org.example.sistema_gestion_vitalexa.dto.ClientResponse;
import org.example.sistema_gestion_vitalexa.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendedor/clients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
public class ClientVendedorController {

    private final ClientService clientService;

    @GetMapping
    public List<ClientResponse> findAll(Authentication auth) {
        return clientService.findByVendedor(auth.getName());
    }

    @GetMapping("/{id}")
    public ClientResponse findById(@PathVariable UUID id) {
        return clientService.findById(id);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(
            @Valid @RequestBody CreateClientRequest request,
            org.springframework.security.core.Authentication auth) {
        ClientResponse response = clientService.create(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ClientResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateClientRequest request) {
        return clientService.update(id, request);
    }

    @PatchMapping("/{id}")
    public ClientResponse patch(
            @PathVariable UUID id,
            @Valid @RequestBody CreateClientRequest request) {
        return clientService.update(id, request);
    }
}
