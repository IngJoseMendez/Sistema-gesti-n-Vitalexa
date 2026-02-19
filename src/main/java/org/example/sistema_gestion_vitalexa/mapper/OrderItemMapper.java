package org.example.sistema_gestion_vitalexa.mapper;

import org.example.sistema_gestion_vitalexa.dto.OrderItemResponse;
import org.example.sistema_gestion_vitalexa.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "product.id", target = "productId")
    @Mapping(target = "productName", expression = "java(getProductName(item))")
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
    // ✅ Campos para productos y promociones especiales
    @Mapping(target = "specialProductId", expression = "java(getSpecialProductId(item))")
    @Mapping(target = "specialPromotionId", expression = "java(getSpecialPromotionId(item))")
    OrderItemResponse toResponse(OrderItem item);

    // ✅ Obtener nombre del producto correcto (especial si existe, sino padre)
    default String getProductName(OrderItem item) {
        if (item.getSpecialProduct() != null) {
            return item.getSpecialProduct().getNombre();
        }
        if (item.getProduct() != null) {
            return item.getProduct().getNombre();
        }
        return "Producto desconocido";
    }

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

    // ✅ Obtener ID del producto especial vinculado (si existe)
    default java.util.UUID getSpecialProductId(OrderItem item) {
        if (item.getSpecialProduct() != null) {
            return item.getSpecialProduct().getId();
        }
        return null;
    }

    // ✅ Obtener ID de la promoción especial vinculada (si existe)
    default java.util.UUID getSpecialPromotionId(OrderItem item) {
        if (item.getSpecialPromotion() != null) {
            return item.getSpecialPromotion().getId();
        }
        return null;
    }
}
