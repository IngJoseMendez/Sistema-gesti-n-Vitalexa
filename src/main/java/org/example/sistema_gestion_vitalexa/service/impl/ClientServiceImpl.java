package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ClientResponse;
import org.example.sistema_gestion_vitalexa.dto.CreateClientRequest;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.ClientMapper;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.service.ClientService;
import org.example.sistema_gestion_vitalexa.service.UserService; // Import UserService
import org.example.sistema_gestion_vitalexa.entity.User; // Import User entity
import org.example.sistema_gestion_vitalexa.repository.UserRepository; // Import UserRepository
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    // IDs for Nina and Gisela (they share clients with each other)
    private static final UUID NINA_ID = UUID.fromString("f3258d03-4419-42a5-b658-003330221c74");
    private static final UUID GISELA_ID = UUID.fromString("180df991-d7d1-4935-81b2-70ad35b4647a");

    private final ClientRepository repository;
    private final ClientMapper clientMapper;
    private final UserService userService; // Inject UserService
    private final UserRepository userRepository; // Inject UserRepository

    @Override
    public Client findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));
    }

    @Override
    public ClientResponse findById(UUID id) {
        return clientMapper.toResponse(findEntityById(id));
    }

    @Override
    public ClientResponse create(CreateClientRequest request, String creatorUsername) {
        // Verificar si ya existe un cliente con ese NIT (único campo obligatorio)
        if (repository.existsByNit(request.nit())) {
            throw new BusinessExeption("Ya existe un cliente con ese NIT");
        }

        // Verificar email solo si se proporciona
        if (request.email() != null && !request.email().isBlank()
                && repository.existsByEmail(request.email())) {
            throw new BusinessExeption("Ya existe un cliente con ese email");
        }

        Client client = clientMapper.toEntity(request);

        // Si no se proporciona nombre, usar el NIT
        if (client.getNombre() == null || client.getNombre().isBlank()) {
            client.setNombre("Cliente " + request.nit());
        }

        client.setTotalCompras(BigDecimal.ZERO);

        // Asignar Vendedor si el creador es VENDEDOR
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new BusinessExeption("Usuario creador no encontrado"));

        if (creator.getRole() == Role.VENDEDOR) {
            client.setVendedorAsignado(creator);
        }

        // AUTO-CREAR USUARIO para el cliente (NIT como usuario y contraseña)
        User user = userService.registerClientUser(client);
        client.setUser(user);

        Client savedClient = repository.save(client);
        return clientMapper.toResponse(savedClient);
    }

    @Override
    public List<ClientResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(clientMapper::toResponse)
                .toList();
    }

    @Override
    public void delete(UUID id) {
        Client client = findEntityById(id);
        repository.delete(client);
    }

    @Override
    public ClientResponse update(UUID id, CreateClientRequest request) {
        Client client = findEntityById(id);

        // Verificar si el email ya está en uso por otro cliente
        if (!client.getEmail().equals(request.email()) &&
                repository.existsByEmail(request.email())) {
            throw new BusinessExeption("El email ya está en uso por otro cliente");
        }

        // Actualizar campos
        client.setNombre(request.nombre());
        client.setEmail(request.email());
        client.setTelefono(request.telefono());
        client.setDireccion(request.direccion());
        client.setNit(request.nit());

        Client updatedClient = repository.save(client);
        return clientMapper.toResponse(updatedClient);
    }

    @Override
    public List<ClientResponse> findByVendedor(String username) {
        User vendedor = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

        List<Client> clients;

        // If vendedor is Nina or Gisela, return shared clients from both
        if (vendedor.getId().equals(NINA_ID) || vendedor.getId().equals(GISELA_ID)) {
            clients = repository.findByVendedorAsignadoIdIn(List.of(NINA_ID, GISELA_ID));
        } else {
            clients = repository.findByVendedorAsignado(vendedor);
        }

        return clients.stream()
                .map(clientMapper::toResponse)
                .toList();
    }

    @Override
    public boolean canVendedorAccessClient(UUID vendedorId, UUID clientId) {
        Client client = findEntityById(clientId);
        UUID clientVendedorId = client.getVendedorAsignado() != null
                ? client.getVendedorAsignado().getId()
                : null;

        // Client without assigned vendedor - allow access
        if (clientVendedorId == null) {
            return true;
        }

        // Direct ownership - vendedor owns this client
        if (clientVendedorId.equals(vendedorId)) {
            return true;
        }

        // Nina/Gisela exception - they share all their clients
        boolean vendedorIsShared = vendedorId.equals(NINA_ID) || vendedorId.equals(GISELA_ID);
        boolean clientBelongsToShared = clientVendedorId.equals(NINA_ID) || clientVendedorId.equals(GISELA_ID);

        if (vendedorIsShared && clientBelongsToShared) {
            return true;
        }

        return false;
    }
}
