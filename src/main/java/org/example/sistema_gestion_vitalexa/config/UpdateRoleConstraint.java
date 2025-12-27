package org.example.sistema_gestion_vitalexa.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateRoleConstraint implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("🔧 Actualizando constraint de roles...");

            // Eliminar constraint viejo
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");

            // Crear constraint nuevo
            jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT users_role_check " +
                    "CHECK (role IN ('ROLE_ADMIN', 'ROLE_OWNER', 'ROLE_VENDEDOR', 'ROLE_EMPACADOR'))");

            log.info("✅ Constraint actualizado correctamente");
        } catch (Exception e) {
            log.warn("⚠️ No se pudo actualizar constraint (probablemente ya está actualizado): {}", e.getMessage());
        }
    }
}
