package org.example.sistema_gestion_vitalexa.config;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        createOrUpdate("DueñoArnold", "12345", Role.OWNER);
        createOrUpdate("AdminHilary", "12345", Role.ADMIN);
        createOrUpdate("vendedor", "1234", Role.VENDEDOR);
        createOrUpdate("Marcelo", "1234", Role.VENDEDOR);
        createOrUpdate("carlos", "1234", Role.VENDEDOR);
    }

    private void create(String username, String password, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        u.setActive(true);
        userRepository.save(u);
    }

    private void createOrUpdate(String username, String password, Role role) {
        User u = userRepository.findByUsername(username)
                .orElse(new User());

        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        u.setActive(true);

        userRepository.save(u);
    }

}
