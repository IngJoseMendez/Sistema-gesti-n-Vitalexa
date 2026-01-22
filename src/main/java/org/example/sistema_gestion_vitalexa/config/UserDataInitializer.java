package org.example.sistema_gestion_vitalexa.config;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class UserDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final ClientRepository clientRepository;

    @Override
    public void run(String... args) {
        createOrUpdate("DueñoArnold", "Azb:iasNZ", Role.OWNER);
        createOrUpdate("AdminHilary", "OL98Jaika", Role.ADMIN);
        createOrUpdate("nina", "ninori123jam", Role.VENDEDOR);
        createOrUpdate("rosario", "1234", Role.VENDEDOR);
        createOrUpdate("luisE", "JKMaoqi213", Role.EMPACADOR);
        createOrUpdateClienteConCuenta(
                "cliente1", "cliente123",
                "Carlos Perez", "carlos@gmail.com", "555-1234", "Calle Falsa 123", "nina", "1234567-8");
        createOrUpdate("mercy", "mercyV123R", Role.VENDEDOR);
        createOrUpdate("gisela", "giselaVR321", Role.VENDEDOR);
        createOrUpdate("arnoldVentas", "ArnoldV123", Role.VENDEDOR);
    }

    private void create(String username, String password, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        u.setActive(true);
        userRepository.save(u);
    }

    private User createOrUpdate(String username, String password, Role role) {
        User u = userRepository.findByUsername(username)
                .orElse(new User());

        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        u.setActive(true);

        return userRepository.save(u);
    }

    private void createOrUpdateClienteConCuenta(
            String username, String password,
            String clientNombre, String email,
            String telefono, String direccion,
            String vendedorUsername,
            String nit) {
        User clienteUser = createOrUpdate(username, password, Role.CLIENTE);

        User vendedor = userRepository.findByUsername(vendedorUsername)
                .orElseThrow(() -> new RuntimeException("Vendedor asignado no existe: " + vendedorUsername));

        Client client = clientRepository.findByUserUsername(username)
                .orElse(Client.builder().build());

        client.setUser(clienteUser);
        client.setNombre(clientNombre);
        client.setEmail(email);
        client.setTelefono(telefono);
        client.setDireccion(direccion);
        client.setActive(true);
        client.setVendedorAsignado(vendedor);
        client.setTotalCompras(BigDecimal.ZERO);
        client.setNit(nit);

        clientRepository.save(client);
    }

}
