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
    OrderResponse toResponse(Order order);

    List<OrderResponse> toResponseList(List<Order> orders);

}
