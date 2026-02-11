package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.CreateSpecialProductRequest;
import org.example.sistema_gestion_vitalexa.dto.SpecialProductResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSpecialProductRequest;
import org.example.sistema_gestion_vitalexa.entity.SpecialProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SpecialProductService {

    SpecialProductResponse create(CreateSpecialProductRequest request);

    SpecialProductResponse update(UUID id, UpdateSpecialProductRequest request);

    SpecialProductResponse findById(UUID id);

    SpecialProduct findEntityById(UUID id);

    Page<SpecialProductResponse> findAll(Pageable pageable);

    List<SpecialProductResponse> findByParent(UUID parentProductId);

    Page<SpecialProductResponse> search(String q, Pageable pageable);

    /** Productos especiales asignados a un vendedor específico */
    List<SpecialProductResponse> findByVendor(UUID vendorUserId);

    Page<SpecialProductResponse> findByVendor(UUID vendorUserId, Pageable pageable);

    void softDelete(UUID id);

    void changeStatus(UUID id, boolean active);
}
