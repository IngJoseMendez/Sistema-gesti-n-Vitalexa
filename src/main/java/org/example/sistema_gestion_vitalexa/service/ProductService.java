package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.CreateProductRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateProductRequest;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    ProductResponse create(CreateProductRequest request);
    ProductResponse update(UUID id, UpdateProductRequest request);
    void softDelete(UUID id);
    void hardDelete(UUID id);

    List<ProductResponse> findAllAdmin();

    // legacy
    List<ProductResponse> findAllActive();

    // paginación pro
    Page<ProductResponse> findAllActive(Pageable pageable);
    Page<ProductResponse> findAllActiveInStock(Pageable pageable);
    Page<ProductResponse> searchActive(String q, Pageable pageable);

    Product findEntityById(UUID id);
    ProductResponse findById(UUID id);
    void changeStatus(UUID id, boolean status);
    List<ProductResponse> findLowStock(int threshold);
}
