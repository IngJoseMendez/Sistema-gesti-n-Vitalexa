package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.CreatePromotionRequest;
import org.example.sistema_gestion_vitalexa.dto.PromotionResponse;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.Promotion;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.PromotionMapper;
import org.example.sistema_gestion_vitalexa.repository.PromotionRepository;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.example.sistema_gestion_vitalexa.service.PromotionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository repository;
    private final PromotionMapper mapper;
    private final ProductService productService;

    @Override
    @Transactional
    public PromotionResponse create(CreatePromotionRequest request) {
        log.info("Creando promoción: {}", request.nombre());

        Promotion promotion = mapper.toEntity(request);

        // Asignar producto principal
        Product mainProduct = productService.findEntityById(request.mainProductId());
        promotion.setMainProduct(mainProduct);

        // Asignar producto gratis si se especifica
        if (request.freeProductId() != null) {
            Product freeProduct = productService.findEntityById(request.freeProductId());
            promotion.setFreeProduct(freeProduct);
        }

        // Configurar flags opcionales con defaults
        if (request.allowStackWithDiscounts() != null) {
            promotion.setAllowStackWithDiscounts(request.allowStackWithDiscounts());
        }

        if (request.requiresAssortmentSelection() != null) {
            promotion.setRequiresAssortmentSelection(request.requiresAssortmentSelection());
        }

        Promotion saved = repository.save(promotion);
        log.info("Promoción creada con ID: {}", saved.getId());

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PromotionResponse update(UUID id, CreatePromotionRequest request) {
        log.info("Actualizando promoción ID: {}", id);

        Promotion promotion = findEntityById(id);

        // Actualizar campos
        promotion.setNombre(request.nombre());
        promotion.setDescripcion(request.descripcion());
        promotion.setType(request.type());
        promotion.setBuyQuantity(request.buyQuantity());
        promotion.setFreeQuantity(request.freeQuantity());
        promotion.setPackPrice(request.packPrice());
        promotion.setValidFrom(request.validFrom());
        promotion.setValidUntil(request.validUntil());

        // Actualizar producto principal
        if (request.mainProductId() != null) {
            Product mainProduct = productService.findEntityById(request.mainProductId());
            promotion.setMainProduct(mainProduct);
        }

        // Actualizar producto gratis
        if (request.freeProductId() != null) {
            Product freeProduct = productService.findEntityById(request.freeProductId());
            promotion.setFreeProduct(freeProduct);
        } else {
            promotion.setFreeProduct(null);
        }

        // Actualizar flags opcionales
        if (request.allowStackWithDiscounts() != null) {
            promotion.setAllowStackWithDiscounts(request.allowStackWithDiscounts());
        }

        if (request.requiresAssortmentSelection() != null) {
            promotion.setRequiresAssortmentSelection(request.requiresAssortmentSelection());
        }

        Promotion updated = repository.save(promotion);
        log.info("Promoción actualizada: {}", id);

        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Eliminando promoción ID: {}", id);
        Promotion promotion = findEntityById(id);
        repository.delete(promotion);
        log.info("Promoción eliminada: {}", id);
    }

    @Override
    @Transactional
    public void changeStatus(UUID id, boolean active) {
        log.info("Cambiando estado de promoción ID: {} a {}", id, active);
        Promotion promotion = findEntityById(id);
        promotion.setActive(active);
        repository.save(promotion);
        log.info("Estado actualizado para promoción: {}", id);
    }

    @Override
    public List<PromotionResponse> findAll() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<PromotionResponse> findAllActive() {
        return repository.findByActiveTrue().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<PromotionResponse> findValidPromotions() {
        LocalDateTime now = LocalDateTime.now();
        return repository.findValidPromotions(now).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public PromotionResponse findById(UUID id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Override
    public Promotion findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Promoción no encontrada"));
    }

    @Override
    public void validatePromotion(UUID id) {
        Promotion promotion = findEntityById(id);

        if (!promotion.getActive()) {
            throw new BusinessExeption("La promoción está inactiva");
        }

        if (!promotion.isValid()) {
            throw new BusinessExeption("La promoción no está dentro del período de validez");
        }

        log.debug("Promoción {} validada correctamente", id);
    }
}
