package org.example.sistema_gestion_vitalexa.dto;

/**
 * DTO for notification data
 * (Updated to force recompile)
 */

public record NotificationData(
                String orderId,
                String productId,
                String productName,
                Integer currentStock,
                Integer reorderPoint) {
}
