package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.NotificationDTO;
import org.example.sistema_gestion_vitalexa.dto.NotificationType;

public interface NotificationService {
    void sendNewOrderNotification(String orderId, String vendorName, String clientName);
    void sendOrderCompletedNotification(String orderId);
    void sendLowStockAlert(String productId, String productName, int currentStock, int reorderPoint);
    void sendOutOfStockAlert(String productId, String productName);

    // ✅ NUEVOS MÉTODOS PARA INVENTARIO
    void sendInventoryUpdate(String productId, String action);
    void sendReembolsoCreated(String reembolsoId, String empacadorName);

    NotificationDTO createNotification(NotificationType type, String title, String message, String targetUrl, Object data);
}
