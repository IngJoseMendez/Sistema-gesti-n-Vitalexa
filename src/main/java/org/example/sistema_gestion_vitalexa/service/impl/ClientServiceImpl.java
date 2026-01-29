package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.AdminCreateClientRequest;
import org.example.sistema_gestion_vitalexa.dto.ClientResponse;
import org.example.sistema_gestion_vitalexa.dto.CreateClientRequest;
import org.example.sistema_gestion_vitalexa.dto.VendedorSimpleDTO;
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

    private static final String NINA_USERNAME = "NinaTorres";
    private static final String GISELA_USERNAME = "YicelaSandoval";
    private static final List<String> SHARED_USERNAMES = List.of(NINA_USERNAME, GISELA_USERNAME);
    private static final List<String> SHARED_USERNAMES_LOWER = List.of(
            NINA_USERNAME.toLowerCase(), GISELA_USERNAME.toLowerCase());

    private final ClientRepository repository;
    private final ClientMapper clientMapper;
    private final UserService userService;
    private final UserRepository userRepository;

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
        // Verificar si ya existe un cliente con ese nombre (establecimiento)
        if (request.nombre() != null && !request.nombre().isBlank()
                && repository.existsByNombre(request.nombre())) {
            throw new BusinessExeption("Ya existe un cliente con ese nombre de establecimiento");
        }

        // Verificar si ya existe un cliente con esa dirección
        if (request.direccion() != null && !request.direccion().isBlank()
                && repository.existsByDireccion(request.direccion())) {
            throw new BusinessExeption("Ya existe un cliente con esa dirección");
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

        return findClientsForVendedorUser(vendedor);
    }

    @Override
    public List<ClientResponse> findByVendedorId(UUID vendedorId) {
        User vendedor = userRepository.findById(vendedorId)
                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

        return findClientsForVendedorUser(vendedor);
    }

    private List<ClientResponse> findClientsForVendedorUser(User vendedor) {
        List<Client> clients;
        String username = vendedor.getUsername();

        // If vendedor is Nina or Gisela, return shared clients from both
        if (SHARED_USERNAMES_LOWER.contains(username.toLowerCase())) {
            clients = repository.findByVendedorAsignadoUsernameIn(SHARED_USERNAMES);
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
        User clientVendedor = client.getVendedorAsignado();

        // Client without assigned vendedor - allow access
        if (clientVendedor == null) {
            return true;
        }

        // Direct ownership - vendedor owns this client
        if (clientVendedor.getId().equals(vendedorId)) {
            return true;
        }

        // Get the vendedor making the request
        User requestingVendedor = userRepository.findById(vendedorId).orElse(null);
        if (requestingVendedor == null) {
            return false;
        }

        // Nina/Gisela exception - they share all their clients
        boolean vendedorIsShared = SHARED_USERNAMES_LOWER.contains(requestingVendedor.getUsername().toLowerCase());
        boolean clientBelongsToShared = SHARED_USERNAMES_LOWER.contains(clientVendedor.getUsername().toLowerCase());

        return vendedorIsShared && clientBelongsToShared;
    }

    @Override
    public ClientResponse createForVendedor(AdminCreateClientRequest request, String adminUsername) {
        // Get admin/owner who is creating the client
        User creator = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new BusinessExeption("Usuario creador no encontrado"));

        // Get target vendedor
        User vendedor = userRepository.findById(request.vendedorId())
                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

        // Validate vendedor role
        if (vendedor.getRole() != Role.VENDEDOR) {
            throw new BusinessExeption("El usuario seleccionado no es un vendedor");
        }

        // Validate unique nombre
        if (request.nombre() != null && !request.nombre().isBlank()
                && repository.existsByNombre(request.nombre())) {
            throw new BusinessExeption("Ya existe un cliente con ese nombre de establecimiento");
        }

        // Validate unique direccion
        if (request.direccion() != null && !request.direccion().isBlank()
                && repository.existsByDireccion(request.direccion())) {
            throw new BusinessExeption("Ya existe un cliente con esa dirección");
        }

        // Build client
        Client client = Client.builder()
                .nombre(request.nombre() != null && !request.nombre().isBlank()
                        ? request.nombre()
                        : "Cliente " + request.nit())
                .email(request.email())
                .telefono(request.telefono())
                .direccion(request.direccion())
                .nit(request.nit())
                .administrador(request.administrador())
                .representanteLegal(request.representanteLegal())
                .vendedorAsignado(vendedor)
                .creadoPor(creator)
                .totalCompras(BigDecimal.ZERO)
                .active(true)
                .build();

        // AUTO-CREATE user for the client (NIT as username and password)
        User user = userService.registerClientUser(client);
        client.setUser(user);

        Client savedClient = repository.save(client);
        return clientMapper.toResponse(savedClient);
    }

    @Override
    public List<VendedorSimpleDTO> getVendedores() {
        return userRepository.findByRole(Role.VENDEDOR)
                .stream()
                .map(u -> new VendedorSimpleDTO(u.getId(), u.getUsername()))
                .toList();
    }
}
