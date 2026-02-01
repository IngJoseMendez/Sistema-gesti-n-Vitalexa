package org.example.sistema_gestion_vitalexa.controller.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.AdminCreateClientRequest;
import org.example.sistema_gestion_vitalexa.dto.ClientResponse;
import org.example.sistema_gestion_vitalexa.dto.VendedorSimpleDTO;
import org.example.sistema_gestion_vitalexa.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class ClientAdminController {

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<List<ClientResponse>> getAllClients() {
        List<ClientResponse> clients = clientService.findAll();
        return ResponseEntity.ok(clients);
    }

    // List all vendedores for dropdown
    @GetMapping("/vendedores")
    public ResponseEntity<List<VendedorSimpleDTO>> getVendedores() {
        return ResponseEntity.ok(clientService.getVendedores());
    }

    // Get clients for a specific vendedor
    @GetMapping("/seller/{id}")
    public ResponseEntity<List<ClientResponse>> getClientsByVendedor(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(clientService.findByVendedorId(id));
    }

    // Create client for a specific vendedor
    @PostMapping
    public ResponseEntity<ClientResponse> createForVendedor(
            @Valid @RequestBody AdminCreateClientRequest request,
            Authentication auth) {
        ClientResponse response = clientService.createForVendedor(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable java.util.UUID id,
            @RequestBody AdminCreateClientRequest request) {
        return ResponseEntity.ok(clientService.updateForAdmin(id, request));
    }
}
