package org.example.sistema_gestion_vitalexa.mapper;

import org.example.sistema_gestion_vitalexa.dto.OrderItemResponse;
import org.example.sistema_gestion_vitalexa.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.nombre", target = "productName")
    @Mapping(target = "subtotal", expression = "java(item.getSubTotal())")
    @Mapping(target = "promotionId", expression = "java(getPromotionId(item))")
    @Mapping(target = "promotionName", expression = "java(getPromotionName(item))")
    @Mapping(source = "isBonified", target = "isBonified")
    @Mapping(source = "isFreightItem", target = "isFreightItem")
    // ✅ NUEVOS: Campos para instancias únicas de promociones y stock negativo
    @Mapping(source = "promotionInstanceId", target = "promotionInstanceId")
    @Mapping(source = "promotionPackPrice", target = "promotionPackPrice")
    @Mapping(source = "promotionGroupIndex", target = "promotionGroupIndex")
    @Mapping(source = "cantidadDescontada", target = "cantidadDescontada")
    @Mapping(source = "cantidadPendiente", target = "cantidadPendiente")
    OrderItemResponse toResponse(OrderItem item);

    // ✅ Obtener ID de la promoción correcta (especial o padre)
    default java.util.UUID getPromotionId(OrderItem item) {
        if (item.getSpecialPromotion() != null) {
            return item.getSpecialPromotion().getId();
        }
        if (item.getPromotion() != null) {
            return item.getPromotion().getId();
        }
        return null;
    }

    // ✅ Obtener nombre de la promoción correcta (especial o padre)
    default String getPromotionName(OrderItem item) {
        if (item.getSpecialPromotion() != null) {
            return item.getSpecialPromotion().getNombre();
        }
        if (item.getPromotion() != null) {
            return item.getPromotion().getNombre();
        }
        return null;
    }
}
