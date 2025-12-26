package org.example.sistema_gestion_vitalexa.mapper;

import org.example.sistema_gestion_vitalexa.dto.SaleGoalResponse;
import org.example.sistema_gestion_vitalexa.entity.SaleGoal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SaleGoalMapper {

    @Mapping(source = "vendedor.id", target = "vendedorId")
    @Mapping(source = "vendedor.username", target = "vendedorUsername")
    @Mapping(target = "percentage", expression = "java(saleGoal.getPercentage())")
    @Mapping(target = "completed", expression = "java(saleGoal.isCompleted())")
    SaleGoalResponse toResponse(SaleGoal saleGoal);
}
