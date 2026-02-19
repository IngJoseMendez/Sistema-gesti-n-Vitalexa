package org.example.sistema_gestion_vitalexa.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductImageService {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${app.upload.dir:uploads/products}")
    private String uploadDir;

    // Cloudinary será null en desarrollo, autowired en producción
    @Autowired(required = false)
    private Cloudinary cloudinary;

    /**
     * Guarda imagen desde array de bytes (para carga masiva/Base64)
     */
    public String saveImage(byte[] data, String originalFilename) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("El array de imagen está vacío");
        }

        // Si estamos en producción Y cloudinary está disponible
        if ("prod".equals(activeProfile) && cloudinary != null) {
            return saveToCloudinary(data, originalFilename);
        } else {
            return saveToLocalDisk(data, originalFilename);
        }
    }

    private String saveToCloudinary(byte[] data, String originalFilename) throws IOException {
        try {
            log.info("Subiendo imagen (bytes) a Cloudinary: {}", originalFilename);

            Map uploadResult = cloudinary.uploader().upload(data,
                    ObjectUtils.asMap(
                            "folder", "vitalexa/products",
                            "resource_type", "auto",
                            "use_filename", true,
                            "unique_filename", true,
                            "filename", originalFilename // Hint to Cloudinary about name
                    ));

            String imageUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");

            log.info("Imagen subida exitosamente a Cloudinary. URL: {}", imageUrl);
            log.debug("Public ID: {}", publicId);

            return imageUrl;

        } catch (Exception e) {
            log.error("Error al subir imagen a Cloudinary", e);
            throw new IOException("Error al subir imagen a Cloudinary: " + e.getMessage(), e);
        }
    }

    private String saveToLocalDisk(byte[] data, String originalFilename) throws IOException {
        try {
            log.info("Guardando imagen (bytes) localmente: {}", originalFilename);

            // Crear directorio si no existe
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.debug("Directorio creado: {}", uploadPath);
            }

            // Generar nombre único
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;

            // Guardar archivo
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, data);

            String relativePath = "/uploads/products/" + filename;
            log.info("Imagen guardada localmente: {}", relativePath);

            return relativePath;

        } catch (IOException e) {
            log.error("Error al guardar imagen localmente", e);
            throw new IOException("Error al guardar imagen: " + e.getMessage(), e);
        }
    }

    /**
     * Guarda imagen en Cloudinary (producción) o disco local (desarrollo)
     */
    public String saveImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen está vacío");
        }

        // Validar tipo de archivo
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen");
        }

        // Si estamos en producción Y cloudinary está disponible
        if ("prod".equals(activeProfile) && cloudinary != null) {
            return saveToCloudinary(file);
        } else {
            return saveToLocalDisk(file);
        }
    }

    /**
     * Guardar en Cloudinary (PRODUCCIÓN)
     */
    private String saveToCloudinary(MultipartFile file) throws IOException {
        try {
            log.info("Subiendo imagen a Cloudinary: {}", file.getOriginalFilename());

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "vitalexa/products",
                            "resource_type", "auto",
                            "use_filename", true,
                            "unique_filename", true));

            String imageUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");

            log.info("Imagen subida exitosamente a Cloudinary. URL: {}", imageUrl);
            log.debug("Public ID: {}", publicId);

            return imageUrl;

        } catch (Exception e) {
            log.error("Error al subir imagen a Cloudinary", e);
            throw new IOException("Error al subir imagen a Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Guardar en disco local (DESARROLLO)
     */
    private String saveToLocalDisk(MultipartFile file) throws IOException {
        try {
            log.info("Guardando imagen localmente: {}", file.getOriginalFilename());

            // Crear directorio si no existe
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.debug("Directorio creado: {}", uploadPath);
            }

            // Generar nombre único
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;

            // Guardar archivo
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = "/uploads/products/" + filename;
            log.info("Imagen guardada localmente: {}", relativePath);

            return relativePath;

        } catch (IOException e) {
            log.error("Error al guardar imagen localmente", e);
            throw new IOException("Error al guardar imagen: " + e.getMessage(), e);
        }
    }

    /**
     * Eliminar imagen (funciona tanto para Cloudinary como local)
     */
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            // Si es URL de Cloudinary
            if (imageUrl.contains("cloudinary.com")) {
                deleteFromCloudinary(imageUrl);
            } else {
                // Es archivo local
                deleteFromLocalDisk(imageUrl);
            }
        } catch (Exception e) {
            log.error("Error al eliminar imagen: {}", imageUrl, e);
        }
    }

    /**
     * Eliminar de Cloudinary
     */
    private void deleteFromCloudinary(String imageUrl) {
        if (cloudinary == null) {
            log.warn("Cloudinary no está configurado, no se puede eliminar imagen");
            return;
        }

        try {
            // Extraer public_id de la URL
            // URL formato:
            // https://res.cloudinary.com/{cloud_name}/image/upload/v{version}/{public_id}.{format}
            String publicId = extractPublicIdFromUrl(imageUrl);

            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                log.info("Imagen eliminada de Cloudinary: {}", publicId);
            }
        } catch (Exception e) {
            log.error("Error al eliminar imagen de Cloudinary", e);
        }
    }

    /**
     * Eliminar de disco local
     */
    private void deleteFromLocalDisk(String imagePath) {
        try {
            // Remover el prefijo /uploads/products/
            String filename = imagePath.replace("/uploads/products/", "");
            Path filePath = Paths.get(uploadDir, filename);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Imagen eliminada localmente: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Error al eliminar archivo local: {}", imagePath, e);
        }
    }

    /**
     * Extraer public_id de URL de Cloudinary
     */
    private String extractPublicIdFromUrl(String url) {
        try {
            // URL formato:
            // https://res.cloudinary.com/{cloud_name}/image/upload/v{version}/{folder}/{filename}.{ext}
            String[] parts = url.split("/upload/");
            if (parts.length == 2) {
                String afterUpload = parts[1];
                // Remover versión si existe (v1234567890/)
                afterUpload = afterUpload.replaceFirst("v\\d+/", "");
                // Remover extensión
                return afterUpload.substring(0, afterUpload.lastIndexOf('.'));
            }
        } catch (Exception e) {
            log.error("Error extrayendo public_id de URL: {}", url, e);
        }
        return null;
    }

    /**
     * Validar si una URL es válida
     */
    public boolean isValidImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return false;
        }
        return imageUrl.startsWith("http") || imageUrl.startsWith("/uploads");
    }
}
