package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateSpecialProductRequest;
import org.example.sistema_gestion_vitalexa.dto.SpecialProductResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSpecialProductRequest;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.ProductTag;
import org.example.sistema_gestion_vitalexa.entity.SpecialProduct;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.repository.ProductRepository;
import org.example.sistema_gestion_vitalexa.repository.ProductTagRepository;
import org.example.sistema_gestion_vitalexa.repository.SpecialProductRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ProductImageService;
import org.example.sistema_gestion_vitalexa.service.SpecialProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SpecialProductServiceImpl implements SpecialProductService {

    private static final Logger log = LoggerFactory.getLogger(SpecialProductServiceImpl.class);

    private final SpecialProductRepository repository;
    private final ProductRepository productRepository;
    private final ProductTagRepository productTagRepository;
    private final UserRepository userRepository;
    private final ProductImageService imageService;

    // ========================================================
    // CREATE
    // ========================================================

    @Override
    public SpecialProductResponse create(CreateSpecialProductRequest request) {
        Product parent = null;

        if (request.parentProductId() != null) {
            // Modo ramificación (hard link)
            parent = productRepository.findById(request.parentProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Producto padre no encontrado: " + request.parentProductId()));

            // Validar que nombre y precio sean distintos al padre
            validateNameAndPriceDifferFromParent(request.nombre(), request.precio(), parent);
        }

        SpecialProduct sp = SpecialProduct.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion() != null
                        ? request.descripcion()
                        : (parent != null ? parent.getDescripcion() : null))
                .precio(request.precio())
                .ownStock(parent == null ? request.stock() : null) // standalone tiene stock propio
                .imageUrl(resolveImageUrl(request, parent))
                .reorderPoint(request.reorderPoint() != null
                        ? request.reorderPoint()
                        : (parent != null ? parent.getReorderPoint() : null))
                .parentProduct(parent)
                .build();

        // Resolver tag
        UUID tagId = request.tagId() != null
                ? request.tagId()
                : (parent != null && parent.getTag() != null ? parent.getTag().getId() : null);
        if (tagId != null) {
            ProductTag tag = productTagRepository.findById(tagId).orElse(null);
            sp.setTag(tag);
        }

        // Asignar vendedores permitidos
        if (request.allowedVendorIds() != null && !request.allowedVendorIds().isEmpty()) {
            Set<User> vendors = new HashSet<>(userRepository.findAllById(request.allowedVendorIds()));
            sp.setAllowedVendors(vendors);
        }

        SpecialProduct saved = repository.save(sp);
        log.info("Producto especial creado: '{}' (linked={}, vendors={})",
                saved.getNombre(), saved.isLinked(), saved.getAllowedVendors().size());
        return toResponse(saved);
    }

    // ========================================================
    // UPDATE
    // ========================================================

    @Override
    public SpecialProductResponse update(UUID id, UpdateSpecialProductRequest request) {
        SpecialProduct sp = findEntityById(id);

        // Si está vinculado, validar que nombre/precio sigan siendo distintos
        if (sp.isLinked()) {
            String newName = request.nombre() != null ? request.nombre() : sp.getNombre();
            var newPrice = request.precio() != null ? request.precio() : sp.getPrecio();
            validateNameAndPriceDifferFromParent(newName, newPrice, sp.getParentProduct());
        }

        if (request.nombre() != null)
            sp.setNombre(request.nombre());
        if (request.descripcion() != null)
            sp.setDescripcion(request.descripcion());
        if (request.precio() != null)
            sp.setPrecio(request.precio());
        if (request.active() != null)
            sp.setActive(request.active());
        if (request.reorderPoint() != null)
            sp.setReorderPoint(request.reorderPoint());

        // Stock solo se puede cambiar en standalone
        if (!sp.isLinked() && request.stock() != null) {
            sp.setOwnStock(request.stock());
        }

        // Imagen
        if (request.imageUrl() != null) {
            sp.setImageUrl(request.imageUrl());
        }

        // Tag
        if (request.tagId() != null) {
            ProductTag tag = productTagRepository.findById(request.tagId()).orElse(null);
            sp.setTag(tag);
        }

        // Actualizar vendedores permitidos (null = no cambiar, lista vacía = quitar
        // todos)
        if (request.allowedVendorIds() != null) {
            if (request.allowedVendorIds().isEmpty()) {
                sp.getAllowedVendors().clear();
            } else {
                Set<User> vendors = new HashSet<>(userRepository.findAllById(request.allowedVendorIds()));
                sp.setAllowedVendors(vendors);
            }
        }

        SpecialProduct saved = repository.save(sp);
        log.info("Producto especial actualizado: '{}'", saved.getNombre());
        return toResponse(saved);
    }

    // ========================================================
    // FIND
    // ========================================================

    @Override
    public SpecialProductResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Override
    public SpecialProduct findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto especial no encontrado: " + id));
    }

    @Override
    public Page<SpecialProductResponse> findAll(Pageable pageable) {
        return repository.findByActiveTrue(pageable).map(this::toResponse);
    }

    @Override
    public List<SpecialProductResponse> findByParent(UUID parentProductId) {
        return repository.findByParentProductId(parentProductId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Page<SpecialProductResponse> search(String q, Pageable pageable) {
        return repository.searchActive(q, pageable).map(this::toResponse);
    }

    @Override
    public List<SpecialProductResponse> findByVendor(UUID vendorUserId) {
        return repository.findActiveByVendorId(vendorUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Page<SpecialProductResponse> findByVendor(UUID vendorUserId, Pageable pageable) {
        return repository.findActiveByVendorId(vendorUserId, pageable).map(this::toResponse);
    }

    // ========================================================
    // DELETE / STATUS
    // ========================================================

    @Override
    public void softDelete(UUID id) {
        SpecialProduct sp = findEntityById(id);
        sp.setActive(false);
        repository.save(sp);
        log.info("Producto especial desactivado: '{}'", sp.getNombre());
    }

    @Override
    public void changeStatus(UUID id, boolean active) {
        SpecialProduct sp = findEntityById(id);
        sp.setActive(active);
        repository.save(sp);
        log.info("Producto especial '{}' -> active={}", sp.getNombre(), active);
    }

    // ========================================================
    // HELPERS
    // ========================================================

    private void validateNameAndPriceDifferFromParent(String nombre, java.math.BigDecimal precio, Product parent) {
        if (parent.getNombre().equalsIgnoreCase(nombre)) {
            throw new IllegalArgumentException(
                    "El nombre del producto especial debe ser diferente al del producto padre ('"
                            + parent.getNombre() + "')");
        }
        if (parent.getPrecio() != null && precio != null
                && parent.getPrecio().compareTo(precio) == 0) {
            throw new IllegalArgumentException(
                    "El precio del producto especial debe ser diferente al del producto padre ("
                            + parent.getPrecio() + ")");
        }
    }

    private String resolveImageUrl(CreateSpecialProductRequest request, Product parent) {
        // 1. Imagen Base64 proporcionada
        if (request.imageBase64() != null && !request.imageBase64().isEmpty()) {
            return handleBase64Image(request.imageBase64(), request.imageFileName());
        }
        // 2. URL directa proporcionada
        if (request.imageUrl() != null && !request.imageUrl().isEmpty()) {
            return request.imageUrl();
        }
        // 3. Heredar del padre
        if (parent != null) {
            return parent.getImageUrl();
        }
        return null;
    }

    private String handleBase64Image(String base64Image, String originalFilename) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }
        try {
            String base64Data = base64Image;
            if (base64Image.contains(",")) {
                base64Data = base64Image.split(",")[1];
            }
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
            return imageService.saveImage(imageBytes,
                    originalFilename != null ? originalFilename : "image.jpg");
        } catch (Exception e) {
            log.error("Error al procesar imagen Base64 para producto especial", e);
            return null;
        }
    }

    private SpecialProductResponse toResponse(SpecialProduct sp) {
        Product parent = sp.getParentProduct();
        Set<User> vendors = sp.getAllowedVendors();

        List<UUID> vendorIds = vendors != null
                ? vendors.stream().map(User::getId).collect(Collectors.toList())
                : List.of();
        List<String> vendorNames = vendors != null
                ? vendors.stream().map(User::getUsername).collect(Collectors.toList())
                : List.of();

        return new SpecialProductResponse(
                sp.getId(),
                sp.getNombre(),
                sp.getDescripcion(),
                sp.getPrecio(),
                sp.getEffectiveStock(),
                sp.getImageUrl(),
                sp.isActive(),
                sp.getReorderPoint(),
                sp.getTag() != null ? sp.getTag().getId() : null,
                sp.getTag() != null ? sp.getTag().getName() : null,
                parent != null ? parent.getId() : null,
                parent != null ? parent.getNombre() : null,
                sp.isLinked(),
                vendorIds,
                vendorNames);
    }
}
