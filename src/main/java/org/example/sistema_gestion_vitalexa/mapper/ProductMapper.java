package org.example.sistema_gestion_vitalexa.mapper;

import org.example.sistema_gestion_vitalexa.dto.CreateProductRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.dto.ReembolsoResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateProductRequest;
import org.example.sistema_gestion_vitalexa.dto.UpdateProductBulkRequest;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.entity.Reembolso;
import org.example.sistema_gestion_vitalexa.entity.ReembolsoItem;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    Product toEntity(CreateProductRequest dto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "descripcion", source = "descripcion")
    @Mapping(target = "precio", source = "precio")
    @Mapping(target = "stock", source = "stock")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "reorderPoint", source = "reorderPoint")
    @Mapping(target = "tagId", expression = "java(product.getTag() != null ? product.getTag().getId() : null)")
    @Mapping(target = "tagName", expression = "java(product.getTag() != null ? product.getTag().getName() : null)")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponseList(List<Product> products);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "active", source = "active")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "descripcion", source = "descripcion")
    @Mapping(target = "precio", source = "precio")
    @Mapping(target = "stock", source = "stock")
    @Mapping(target = "imageUrl", source = "imageUrl")
    void updateEntity(UpdateProductRequest dto, @MappingTarget Product product);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "active", source = "active")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "descripcion", source = "descripcion")
    @Mapping(target = "precio", source = "precio")
    @Mapping(target = "stock", source = "stock")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "reorderPoint", source = "reorderPoint")
    void updateEntity(UpdateProductBulkRequest dto, @MappingTarget Product product);

    @Mapping(target = "empacadorUsername", source = "empacador.username")
    @Mapping(target = "items", expression = "java(mapReembolsoItems(reembolso.getItems()))")
    @Mapping(target = "estado", expression = "java(reembolso.getEstado().toString())")
    ReembolsoResponse toReembolsoResponse(Reembolso reembolso);

    default List<ReembolsoResponse.ReembolsoItemResponse> mapReembolsoItems(List<ReembolsoItem> items) {
        return items.stream()
                .map(item -> ReembolsoResponse.ReembolsoItemResponse.builder()
                        .productoId(item.getProducto().getId())
                        .productoNombre(item.getProducto().getNombre())
                        .productoImageUrl(item.getProducto().getImageUrl())
                        .cantidad(item.getCantidad())
                        .build())
                .collect(Collectors.toList());
    }

    List<ReembolsoResponse> toReembolsoResponseList(List<Reembolso> reembolsos);
}
