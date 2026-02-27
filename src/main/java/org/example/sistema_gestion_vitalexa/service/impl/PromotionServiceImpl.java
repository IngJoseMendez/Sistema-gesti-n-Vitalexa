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

        // Asignar producto principal (Opcional para promociones genéricas)
        if (request.mainProductId() != null) {
            Product mainProduct = productService.findEntityById(request.mainProductId());
            promotion.setMainProduct(mainProduct);
        }

        // Asignar items de regalo
        if (request.giftItems() != null && !request.giftItems().isEmpty()) {
            List<org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem> giftItems = new java.util.ArrayList<>();
            for (org.example.sistema_gestion_vitalexa.dto.GiftItemDTO itemDto : request.giftItems()) {
                Product giftProduct = productService.findEntityById(itemDto.productId());
                giftItems.add(org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem.builder()
                        .promotion(promotion)
                        .product(giftProduct)
                        .quantity(itemDto.quantity())
                        .build());
            }
            promotion.setGiftItems(giftItems);
        }

        // Configurar flags opcionales con defaults
        if (request.allowStackWithDiscounts() != null) {
            promotion.setAllowStackWithDiscounts(request.allowStackWithDiscounts());
        }

        // Configurar requiresAssortmentSelection basado en el TIPO
        if (request.type() == org.example.sistema_gestion_vitalexa.enums.PromotionType.BUY_GET_FREE) {
            promotion.setRequiresAssortmentSelection(true);
        } else {
            promotion.setRequiresAssortmentSelection(false);
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
        promotion.setPackPrice(request.packPrice());
        promotion.setValidFrom(request.validFrom());
        promotion.setValidUntil(request.validUntil());

        // Actualizar producto principal
        if (request.mainProductId() != null) {
            Product mainProduct = productService.findEntityById(request.mainProductId());
            promotion.setMainProduct(mainProduct);
        }

        // Actualizar items de regalo
        if (promotion.getGiftItems() != null) {
            promotion.getGiftItems().clear(); // Limpiar existentes (orphanRemoval se encargará)
        } else {
            promotion.setGiftItems(new java.util.ArrayList<>());
        }

        if (request.giftItems() != null && !request.giftItems().isEmpty()) {
            for (org.example.sistema_gestion_vitalexa.dto.GiftItemDTO itemDto : request.giftItems()) {
                Product giftProduct = productService.findEntityById(itemDto.productId());
                promotion.getGiftItems().add(org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem.builder()
                        .promotion(promotion)
                        .product(giftProduct)
                        .quantity(itemDto.quantity())
                        .build());
            }
        }

        // Actualizar flags opcionales
        if (request.allowStackWithDiscounts() != null) {
            promotion.setAllowStackWithDiscounts(request.allowStackWithDiscounts());
        }

        // Sincronizar requiresAssortmentSelection con el tipo
        if (promotion.getType() == org.example.sistema_gestion_vitalexa.enums.PromotionType.BUY_GET_FREE) {
            promotion.setRequiresAssortmentSelection(true);
        } else {
            promotion.setRequiresAssortmentSelection(false);
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
    public List<PromotionResponse> findValidPromotionsEager() {
        LocalDateTime now = LocalDateTime.now();
        return repository.findValidPromotionsEager(now).stream()
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
