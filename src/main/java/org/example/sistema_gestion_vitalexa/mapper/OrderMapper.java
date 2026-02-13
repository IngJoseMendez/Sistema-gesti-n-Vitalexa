package org.example.sistema_gestion_vitalexa.mapper;

import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = OrderItemMapper.class)
public interface OrderMapper {

    @Mapping(source = "vendedor.username", target = "vendedor")
    @Mapping(source = "cliente.nombre", target = "cliente")
    @Mapping(source = "estado", target = "estado")
    @Mapping(target = "isSROrder", expression = "java(order.getNotas() != null && order.getNotas().contains(\"[S/R]\"))")
    @Mapping(target = "isPromotionOrder", expression = "java(order.getNotas() != null && order.getNotas().contains(\"[Promoción]\"))")
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

    default java.util.List<java.util.UUID> mapPromotionIds(Order order) {
        if (order.getItems() == null)
            return java.util.Collections.emptyList();

        java.util.Map<java.util.UUID, org.example.sistema_gestion_vitalexa.entity.OrderItem> uniqueInstances = new java.util.HashMap<>();

        order.getItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem()) && i.getPromotion() != null)
                .forEach(i -> {
                    java.util.UUID key = i.getPromotionInstanceId() != null
                            ? i.getPromotionInstanceId()
                            : java.util.UUID.randomUUID();
                    uniqueInstances.putIfAbsent(key, i);
                });

        return uniqueInstances.values().stream()
                .map(i -> i.getPromotion().getId())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    List<OrderResponse> toResponseList(List<Order> orders);

}
