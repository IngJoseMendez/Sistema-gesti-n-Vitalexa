package org.example.sistema_gestion_vitalexa.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.entity.ProductTag;
import org.example.sistema_gestion_vitalexa.repository.ProductTagRepository;
import org.springframework.stereotype.Component;

/**
 * Inicializador de etiquetas del sistema
 * Asegura que la etiqueta "S/R" exista en la base de datos
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductTagInitializer {

    private final ProductTagRepository productTagRepository;

    private static final String SR_TAG_NAME = "S/R";

    @PostConstruct
    public void initializeSystemTags() {
        // Verificar si ya existe la etiqueta S/R
        var existingSRTag = productTagRepository.findSRTag();

        if (existingSRTag.isEmpty()) {
            log.info("Creando etiqueta del sistema 'S/R'...");
            ProductTag srTag = ProductTag.builder()
                    .name(SR_TAG_NAME)
                    .isSystem(true)
                    .build();

            productTagRepository.save(srTag);
            log.info("Etiqueta del sistema 'S/R' creada exitosamente");
        } else {
            log.debug("Etiqueta del sistema 'S/R' ya existe");
        }
    }
}

