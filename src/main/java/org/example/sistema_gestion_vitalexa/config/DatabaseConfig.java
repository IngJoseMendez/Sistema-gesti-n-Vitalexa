package org.example.sistema_gestion_vitalexa.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // Repair metadata (fix checksum mismatch)
            flyway.repair();
            // Proceed with migration
            flyway.migrate();
        };
    }
}
