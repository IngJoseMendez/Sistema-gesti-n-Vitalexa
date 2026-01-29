package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.AdminCreateClientRequest;
import org.example.sistema_gestion_vitalexa.dto.ClientResponse;
import org.example.sistema_gestion_vitalexa.dto.CreateClientRequest;
import org.example.sistema_gestion_vitalexa.dto.VendedorSimpleDTO;
import org.example.sistema_gestion_vitalexa.entity.Client;

import java.util.List;
import java.util.UUID;

public interface ClientService {
    ClientResponse findById(UUID id);

    Client findEntityById(UUID id);

    ClientResponse create(CreateClientRequest request, String creatorUsername);

    ClientResponse update(UUID id, CreateClientRequest request);

    List<ClientResponse> findAll();

    void delete(UUID id);

    // Filter clients by vendedor (with Nina/Gisela exception)
    List<ClientResponse> findByVendedor(String username);

    // Admin: Find by specific vendedor ID
    List<ClientResponse> findByVendedorId(UUID vendedorId);

    // Validate if vendedor can access a specific client
    boolean canVendedorAccessClient(UUID vendedorId, UUID clientId);

    // Admin/Owner: Create client for a specific vendedor
    ClientResponse createForVendedor(AdminCreateClientRequest request, String adminUsername);

    // Get list of vendedores for dropdown
    List<VendedorSimpleDTO> getVendedores();
}
