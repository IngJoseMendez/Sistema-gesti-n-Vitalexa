package org.example.sistema_gestion_vitalexa.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
@Slf4j
public class ImageController {

    @Value("${app.upload.dir:uploads/products}")
    private String uploadDir;

    /**
     * Sirve imágenes de productos públicamente
     */
    @GetMapping("/products/{filename:.+}")
    public ResponseEntity<Resource> getProductImage(@PathVariable String filename) {
        try {
            // Resolver la ruta del archivo
            Path filePath = Paths.get(uploadDir)
                    .toAbsolutePath()
                    .normalize()
                    .resolve(filename);

            log.debug("🔍 Buscando imagen en: {}", filePath);

            // Verificar que el archivo existe y es legible
            if (!Files.exists(filePath)) {
                log.warn("⚠️ Imagen no encontrada: {}", filename);
                return ResponseEntity.notFound().build();
            }

            if (!Files.isReadable(filePath)) {
                log.error("❌ Imagen no legible: {}", filename);
                return ResponseEntity.status(403).build();
            }

            // Crear recurso
            Resource resource = new UrlResource(filePath.toUri());

            // Determinar tipo de contenido
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            log.debug("✅ Sirviendo imagen: {} (tipo: {})", filename, contentType);

            // Retornar imagen
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("❌ Error sirviendo imagen: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
