package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.AddAssortmentItemRequest;
import org.example.sistema_gestion_vitalexa.dto.CreateHistoricalInvoiceRequest;
import org.example.sistema_gestion_vitalexa.dto.OrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;

import java.util.List;
import java.util.UUID;

public interface OrdenService {

    // 🔹 Crear orden (VENDEDOR)
    OrderResponse createOrder(OrderRequestDto request, String username);

    // 🔹 Cambiar estado (ADMIN / OWNER)
    OrderResponse cambiarEstadoOrden(UUID orderId, OrdenStatus nuevoEstado);

    // 🔹 ADMIN / OWNER
    OrderResponse findById(UUID orderId);

    List<OrderResponse> findAll();

    // 🔹 VENDEDOR (solo sus órdenes)
    List<OrderResponse> findMyOrders(String username);

    OrderResponse findMyOrderById(UUID id, String username);

    OrderResponse updateOrder(UUID orderId, OrderRequestDto request);

    // 🔹 Anular Orden (ADMIN)
    void annulOrder(UUID orderId, String reason);

    List<OrderResponse> findByEstado(OrdenStatus estado);

    /**
     * Agregar productos surtidos a una promoción en una orden
     * (Solo Admin/Owner)
     */
    void addPromotionAssortment(UUID orderId, UUID promotionId, List<AddAssortmentItemRequest> items);

    /**
     * Actualizar ETA de un item sin stock
     */
    void updateItemEta(UUID orderId, UUID itemId, java.time.LocalDate eta, String note);

    /**
     * Crear factura histórica para cuadre de caja
     * (Solo Owner - para facturas anteriores al sistema)
     */
    OrderResponse createHistoricalInvoice(CreateHistoricalInvoiceRequest request, String ownerUsername);

}
