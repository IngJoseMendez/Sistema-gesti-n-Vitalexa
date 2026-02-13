package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.CreateSpecialPromotionRequest;
import org.example.sistema_gestion_vitalexa.dto.SpecialPromotionResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSpecialPromotionRequest;
import org.example.sistema_gestion_vitalexa.entity.SpecialPromotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SpecialPromotionService {

    SpecialPromotionResponse create(CreateSpecialPromotionRequest request);

    SpecialPromotionResponse update(UUID id, UpdateSpecialPromotionRequest request);

    SpecialPromotionResponse findById(UUID id);

    SpecialPromotion findEntityById(UUID id);

    Page<SpecialPromotionResponse> findAll(Pageable pageable);

    List<SpecialPromotionResponse> findByParent(UUID parentPromotionId);

    Page<SpecialPromotionResponse> search(String query, Pageable pageable);

    List<SpecialPromotionResponse> findByVendor(UUID vendorUserId);

    Page<SpecialPromotionResponse> findByVendor(UUID vendorUserId, Pageable pageable);

    void softDelete(UUID id);

    void changeStatus(UUID id, boolean active);
}
