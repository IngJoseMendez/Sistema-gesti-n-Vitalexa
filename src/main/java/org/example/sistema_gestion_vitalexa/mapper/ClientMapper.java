package org.example.sistema_gestion_vitalexa.mapper;

import org.example.sistema_gestion_vitalexa.dto.ClientResponse;
import org.example.sistema_gestion_vitalexa.dto.CreateClientRequest;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClientMapper {
    @Mapping(target = "vendedorAsignadoNombre", source = "vendedorAsignado.username")
    @Mapping(target = "creadoPorNombre", source = "creadoPor.username")
    ClientResponse toResponse(Client client);

    List<ClientResponse> toResponseList(List<Client> clients);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    Client toEntity(CreateClientRequest createClientRequest);
}
