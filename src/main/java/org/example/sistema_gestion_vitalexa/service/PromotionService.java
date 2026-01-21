package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.CreatePromotionRequest;
import org.example.sistema_gestion_vitalexa.dto.PromotionResponse;
import org.example.sistema_gestion_vitalexa.entity.Promotion;

import java.util.List;
import java.util.UUID;

public interface PromotionService {

    /**
     * Crear una nueva promoción (Solo Admin/Owner)
     */
    PromotionResponse create(CreatePromotionRequest request);

    /**
     * Actualizar una promoción existente (Solo Admin/Owner)
     */
    PromotionResponse update(UUID id, CreatePromotionRequest request);

    /**
     * Eliminar una promoción (Solo Admin/Owner)
     */
    void delete(UUID id);

    /**
     * Cambiar estado activo/inactivo de una promoción
     */
    void changeStatus(UUID id, boolean active);

    /**
     * Obtener todas las promociones (Admin/Owner)
     */
    List<PromotionResponse> findAll();

    /**
     * Obtener solo promociones activas (Vendedor)
     */
    List<PromotionResponse> findAllActive();

    /**
     * Obtener promociones válidas (activas y dentro del período de validez)
     */
    List<PromotionResponse> findValidPromotions();

    /**
     * Obtener una promoción por ID
     */
    PromotionResponse findById(UUID id);

    /**
     * Obtener entidad Promotion (uso interno)
     */
    Promotion findEntityById(UUID id);

    /**
     * Validar si una promoción es válida (vigencia y estado)
     */
    void validatePromotion(UUID id);
}
