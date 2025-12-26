package org.example.sistema_gestion_vitalexa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class ProductImageService {

    @Value("${app.upload.dir:uploads/products}")
    private String uploadDir;

    /**
     * Guarda una imagen y retorna el nombre único del archivo
     */
    public String saveImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Archivo vacío o nulo");
        }

        // Validar que sea una imagen
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen");
        }

        // Obtener extensión original
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Generar nombre único
        String uniqueFilename = "product_" + UUID.randomUUID() + fileExtension;

        // Crear directorio si no existe
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("📁 Directorio de uploads creado: {}", uploadPath);
        }

        // Guardar archivo
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("✅ Imagen guardada: {} (tamaño: {} bytes)", uniqueFilename, file.getSize());

        return uniqueFilename;
    }

    /**
     * Elimina una imagen del sistema de archivos
     */
    public void deleteImage(String filename) {
        if (filename == null || filename.isEmpty()) {
            return;
        }

        try {
            Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                log.info("🗑️ Imagen eliminada: {}", filename);
            } else {
                log.warn("⚠️ Imagen no encontrada para eliminar: {}", filename);
            }
        } catch (IOException e) {
            log.error("❌ Error eliminando imagen: {}", filename, e);
        }
    }

    /**
     * Verifica si una imagen existe
     */
    public boolean imageExists(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
        return Files.exists(filePath);
    }
}
