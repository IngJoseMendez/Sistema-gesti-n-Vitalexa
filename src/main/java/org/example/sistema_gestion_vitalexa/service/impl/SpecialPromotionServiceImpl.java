package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateSpecialPromotionRequest;
import org.example.sistema_gestion_vitalexa.dto.SpecialPromotionResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSpecialPromotionRequest;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.Promotion;
import org.example.sistema_gestion_vitalexa.entity.SpecialPromotion;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.repository.ProductRepository;
import org.example.sistema_gestion_vitalexa.repository.PromotionRepository;
import org.example.sistema_gestion_vitalexa.repository.SpecialPromotionRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.SpecialPromotionService;
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
public class SpecialPromotionServiceImpl implements SpecialPromotionService {

    private static final Logger log = LoggerFactory.getLogger(SpecialPromotionServiceImpl.class);

    private final SpecialPromotionRepository repository;
    private final PromotionRepository promotionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // ========================================================
    // CREATE
    // ========================================================

    @Override
    public SpecialPromotionResponse create(CreateSpecialPromotionRequest request) {
        Promotion parent = null;
        Product mainProduct = null;

        if (request.parentPromotionId() != null) {
            parent = promotionRepository.findById(request.parentPromotionId())
                    .orElseThrow(() -> new RuntimeException(
                            "Promoción padre no encontrada: " + request.parentPromotionId()));
        }

        if (request.mainProductId() != null) {
            mainProduct = productRepository.findById(request.mainProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Producto principal no encontrado: " + request.mainProductId()));
        } else if (parent != null) {
            mainProduct = parent.getMainProduct();
        }

        SpecialPromotion sp = SpecialPromotion.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion() != null
                        ? request.descripcion()
                        : (parent != null ? parent.getDescripcion() : null))
                .type(request.type() != null ? request.type() : (parent != null ? parent.getType() : null))
                .buyQuantity(request.buyQuantity() != null
                        ? request.buyQuantity()
                        : (parent != null ? parent.getBuyQuantity() : null))
                .packPrice(request.packPrice() != null
                        ? request.packPrice()
                        : (parent != null ? parent.getPackPrice() : null))
                .mainProduct(mainProduct)
                .parentPromotion(parent)
                .active(request.active() != null ? request.active() : true)
                .validFrom(request.validFrom() != null
                        ? request.validFrom()
                        : (parent != null ? parent.getValidFrom() : null))
                .validUntil(request.validUntil() != null
                        ? request.validUntil()
                        : (parent != null ? parent.getValidUntil() : null))
                .build();

        // Asignar vendedores permitidos
        if (request.allowedVendorIds() != null && !request.allowedVendorIds().isEmpty()) {
            Set<User> vendors = new HashSet<>(userRepository.findAllById(request.allowedVendorIds()));
            sp.setAllowedVendors(vendors);
        }

        SpecialPromotion saved = repository.save(sp);
        log.info("Promoción especial creada: '{}' (linked={}, vendors={})",
                saved.getNombre(), saved.isLinked(), saved.getAllowedVendors().size());
        return toResponse(saved);
    }

    // ========================================================
    // UPDATE
    // ========================================================

    @Override
    public SpecialPromotionResponse update(UUID id, UpdateSpecialPromotionRequest request) {
        SpecialPromotion sp = findEntityById(id);

        if (request.nombre() != null)
            sp.setNombre(request.nombre());
        if (request.descripcion() != null)
            sp.setDescripcion(request.descripcion());
        if (request.type() != null)
            sp.setType(request.type());
        if (request.buyQuantity() != null)
            sp.setBuyQuantity(request.buyQuantity());
        if (request.packPrice() != null)
            sp.setPackPrice(request.packPrice());
        if (request.active() != null)
            sp.setActive(request.active());
        if (request.validFrom() != null)
            sp.setValidFrom(request.validFrom());
        if (request.validUntil() != null)
            sp.setValidUntil(request.validUntil());

        if (request.mainProductId() != null) {
            Product mainProduct = productRepository.findById(request.mainProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Producto principal no encontrado: " + request.mainProductId()));
            sp.setMainProduct(mainProduct);
        }

        // Actualizar vendedores permitidos
        if (request.allowedVendorIds() != null) {
            if (request.allowedVendorIds().isEmpty()) {
                sp.getAllowedVendors().clear();
            } else {
                Set<User> vendors = new HashSet<>(userRepository.findAllById(request.allowedVendorIds()));
                sp.setAllowedVendors(vendors);
            }
        }

        SpecialPromotion saved = repository.save(sp);
        log.info("Promoción especial actualizada: '{}'", saved.getNombre());
        return toResponse(saved);
    }

    // ========================================================
    // FIND
    // ========================================================

    @Override
    public SpecialPromotionResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Override
    public SpecialPromotion findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promoción especial no encontrada: " + id));
    }

    @Override
    public Page<SpecialPromotionResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public List<SpecialPromotionResponse> findByParent(UUID parentPromotionId) {
        return repository.findByParentPromotionId(parentPromotionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Page<SpecialPromotionResponse> search(String query, Pageable pageable) {
        return repository.searchActive(query, pageable).map(this::toResponse);
    }

    @Override
    public List<SpecialPromotionResponse> findByVendor(UUID vendorUserId) {
        User user = userRepository.findById(vendorUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.ADMIN ||
                user.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.OWNER) {
            return repository.findAll().stream() // findActiveTrue() handled by repository query usually, but using
                                                 // findAll for now or create method
                    .filter(SpecialPromotion::isActive)
                    .map(this::toResponse)
                    .toList();
        }

        return repository.findActiveByVendorId(vendorUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Page<SpecialPromotionResponse> findByVendor(UUID vendorUserId, Pageable pageable) {
        User user = userRepository.findById(vendorUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.ADMIN ||
                user.getRole() == org.example.sistema_gestion_vitalexa.enums.Role.OWNER) {
            return repository.findByActiveTrue(pageable).map(this::toResponse);
        }

        return repository.findActiveByVendorId(vendorUserId, pageable).map(this::toResponse);
    }

    // ========================================================
    // DELETE / STATUS
    // ========================================================

    @Override
    public void softDelete(UUID id) {
        SpecialPromotion sp = findEntityById(id);
        sp.setActive(false);
        repository.save(sp);
        log.info("Promoción especial desactivada: '{}'", sp.getNombre());
    }

    @Override
    public void changeStatus(UUID id, boolean active) {
        SpecialPromotion sp = findEntityById(id);
        sp.setActive(active);
        repository.save(sp);
        log.info("Promoción especial '{}' -> active={}", sp.getNombre(), active);
    }

    // ========================================================
    // HELPERS
    // ========================================================

    private SpecialPromotionResponse toResponse(SpecialPromotion sp) {
        Promotion parent = sp.getParentPromotion();
        Set<User> vendors = sp.getAllowedVendors();

        List<UUID> vendorIds = vendors != null
                ? vendors.stream().map(User::getId).toList()
                : List.of();
        List<String> vendorNames = vendors != null
                ? vendors.stream().map(User::getUsername).toList()
                : List.of();

        return new SpecialPromotionResponse(
                sp.getId(),
                sp.getNombre(),
                sp.getDescripcion(),
                sp.getEffectiveType(),
                sp.getEffectiveBuyQuantity(),
                sp.getEffectivePackPrice(),
                sp.getMainProduct() != null ? sp.getMainProduct().getId() : null,
                sp.getMainProduct() != null ? sp.getMainProduct().getNombre() : null,
                sp.isActive(),
                sp.getValidFrom(),
                sp.getValidUntil(),
                parent != null ? parent.getId() : null,
                parent != null ? parent.getNombre() : null,
                sp.isLinked(),
                vendorIds,
                vendorNames);
    }
}
