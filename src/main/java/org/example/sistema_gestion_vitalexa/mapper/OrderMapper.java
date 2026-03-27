package org.example.sistema_gestion_vitalexa.mapper;

import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = OrderItemMapper.class)
public interface OrderMapper {

    @Mapping(source = "vendedor.username", target = "vendedor")
    @Mapping(source = "cliente.nombre", target = "cliente")
    @Mapping(source = "estado", target = "estado")
    @Mapping(target = "isSROrder", expression = "java(isSROrderByItemsOrNotes(order))")
    @Mapping(target = "isPromotionOrder", expression = "java((order.getNotas() != null && order.getNotas().contains(\"[Promoción]\")) || !mapPromotionIds(order).isEmpty())")
    @Mapping(target = "isHistorical", expression = "java(order.getNotas() != null && order.getNotas().contains(\"[HISTÓRICA]\"))")
    @Mapping(source = "isFreightBonified", target = "isFreightBonified")
    @Mapping(source = "freightCustomText", target = "freightCustomText")
    @Mapping(source = "freightQuantity", target = "freightQuantity")
    @Mapping(source = "cliente.representanteLegal", target = "representanteLegal")
    @Mapping(source = "cliente.telefono", target = "clientePhone")
    @Mapping(source = "cliente.direccion", target = "clienteAddress")
    @Mapping(source = "cliente.nit", target = "clienteNit")
    @Mapping(source = "cliente.email", target = "clienteEmail")
    @Mapping(target = "promotionIds", expression = "java(mapPromotionIds(order))")
    OrderResponse toResponse(Order order);

    /**
     * Detecta si una orden es S/R revisando primero las notas y luego los tags
     * de los productos en los items. Garantiza compatibilidad retroactiva con
     * órdenes antiguas que tienen productos S/R pero no tienen [S/R] en sus notas.
     */
    default boolean isSROrderByItemsOrNotes(Order order) {
        // Método primario: notas contienen el tag [S/R]
        if (order.getNotas() != null && order.getNotas().contains("[S/R]")) {
            return true;
        }
        // Fallback: verificar si algún item no-promocional y no-flete tiene un producto S/R
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (Boolean.TRUE.equals(item.getIsPromotionItem())) continue;
                if (Boolean.TRUE.equals(item.getIsFreightItem())) continue;
                org.example.sistema_gestion_vitalexa.entity.Product product = item.getProduct();
                if (product != null && product.getTag() != null
                        && "S/R".equalsIgnoreCase(product.getTag().getName())) {
                    return true;
                }
                org.example.sistema_gestion_vitalexa.entity.SpecialProduct sp = item.getSpecialProduct();
                if (sp != null && sp.getTag() != null
                        && "S/R".equalsIgnoreCase(sp.getTag().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    default java.util.List<java.util.UUID> mapPromotionIds(Order order) {
        if (order.getItems() == null)
            return java.util.Collections.emptyList();

        java.util.Map<java.util.UUID, org.example.sistema_gestion_vitalexa.entity.OrderItem> uniqueInstances = new java.util.HashMap<>();

        order.getItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem()) &&
                        (i.getPromotion() != null || i.getSpecialPromotion() != null))
                .forEach(i -> {
                    java.util.UUID key = i.getPromotionInstanceId() != null
                            ? i.getPromotionInstanceId()
                            : java.util.UUID.randomUUID();
                    uniqueInstances.putIfAbsent(key, i);
                });

        return uniqueInstances.values().stream()
                .map(i -> {
                    // ✅ Priorizar SpecialPromotion sobre Promotion
                    if (i.getSpecialPromotion() != null) {
                        return i.getSpecialPromotion().getId();
                    }
                    return i.getPromotion().getId();
                })
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    List<OrderResponse> toResponseList(List<Order> orders);

}
