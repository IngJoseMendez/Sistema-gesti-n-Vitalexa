package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.ApplyDiscountRequest;
import org.example.sistema_gestion_vitalexa.dto.DiscountResponse;
import org.example.sistema_gestion_vitalexa.enums.DiscountType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para gestión de descuentos
 */
public interface DiscountService {

    /**
     * Aplica un descuento predefinido (Admin)
     */
    DiscountResponse applyPresetDiscount(UUID orderId, DiscountType type, String adminUsername);

    /**
     * Aplica un descuento personalizado (Admin)
     */
    DiscountResponse applyCustomDiscount(ApplyDiscountRequest request, String adminUsername);

    /**
     * Revoca un descuento (Owner)
     */
    DiscountResponse revokeDiscount(UUID discountId, String ownerUsername);

    /**
     * Aplica un descuento adicional (Owner)
     */
    DiscountResponse applyOwnerDiscount(ApplyDiscountRequest request, String ownerUsername);

    /**
     * Obtiene los descuentos de una orden
     */
    List<DiscountResponse> getDiscountsByOrderId(UUID orderId);

    /**
     * Recalcula el total con descuento de una orden
     */
    void recalculateDiscountedTotal(UUID orderId);
}
