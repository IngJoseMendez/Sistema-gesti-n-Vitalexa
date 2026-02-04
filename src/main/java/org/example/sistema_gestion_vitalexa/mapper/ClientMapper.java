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
public abstract class ClientMapper {

    @org.springframework.beans.factory.annotation.Autowired
    protected org.example.sistema_gestion_vitalexa.repository.ClientRepository clientRepository;

    @Mapping(target = "vendedorAsignadoNombre", source = "vendedorAsignado.username")
    @Mapping(target = "creadoPorNombre", source = "creadoPor.username")
    @Mapping(target = "totalCompras", expression = "java(calculateTotal(client))")
    public abstract ClientResponse toResponse(Client client);

    public abstract List<ClientResponse> toResponseList(List<Client> clients);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract Client toEntity(CreateClientRequest createClientRequest);

    protected java.math.BigDecimal calculateTotal(Client client) {
        if (client.getId() == null)
            return java.math.BigDecimal.ZERO;
        return clientRepository.calculateTotalPurchases(client.getId());
    }
}
