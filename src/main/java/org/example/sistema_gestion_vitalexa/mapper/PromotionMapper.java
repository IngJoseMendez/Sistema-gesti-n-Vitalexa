package org.example.sistema_gestion_vitalexa.mapper;

import org.example.sistema_gestion_vitalexa.dto.CreatePromotionRequest;
import org.example.sistema_gestion_vitalexa.dto.PromotionResponse;
import org.example.sistema_gestion_vitalexa.entity.Promotion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { ProductMapper.class })
public interface PromotionMapper {

    @Mapping(target = "isValid", expression = "java(promotion.isValid())")
    PromotionResponse toResponse(Promotion promotion);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "mainProduct", ignore = true)
    // GiftItems se manejan manualmente en el servicio
    @Mapping(target = "giftItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "active", constant = "true")
    Promotion toEntity(CreatePromotionRequest request);

    @Mapping(target = "id", source = "gift.id")
    @Mapping(target = "product", source = "gift.product")
    @Mapping(target = "quantity", source = "gift.quantity")
    org.example.sistema_gestion_vitalexa.dto.GiftItemResponse toGiftItemResponse(
            org.example.sistema_gestion_vitalexa.entity.PromotionGiftItem gift);
}
