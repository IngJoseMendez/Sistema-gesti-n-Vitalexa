package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.AddAssortmentItemRequest;
import org.example.sistema_gestion_vitalexa.dto.CompleteOrderRequest;
import org.example.sistema_gestion_vitalexa.dto.CreateHistoricalInvoiceRequest;
import org.example.sistema_gestion_vitalexa.dto.OrderCreationResult;
import org.example.sistema_gestion_vitalexa.dto.OrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface OrdenService {

    // 🔹 Crear orden (VENDEDOR / ADMIN)
    // Retorna OrderCreationResult que indica si se hizo split S/R
    OrderCreationResult createOrder(OrderRequestDto request, String username);

    // 🔹 Cambiar estado (ADMIN / OWNER)
    OrderResponse cambiarEstadoOrden(UUID orderId, OrdenStatus nuevoEstado);

    // 🔹 Completar orden con fecha opcional (ADMIN / OWNER)
    /**
     * Marca la orden como COMPLETADA asignando la fecha provista en el request,
     * o la fecha actual si {@code request.completedAt()} es null.
     * Recalcula la meta del vendedor y registra auditoría en el campo notas.
     *
     * @param orderId  ID de la orden a completar
     * @param request  Fecha manual (opcional) y nota de auditoría
     * @param username Usuario que ejecuta la acción
     */
    OrderResponse completeOrder(UUID orderId, CompleteOrderRequest request, String username);

    // 🔹 ADMIN / OWNER
    OrderResponse findById(UUID orderId);

    List<OrderResponse> findAll();

    /**
     * Lista paginada de todas las órdenes (admin).
     *
     * @param page     número de página (0-based)
     * @param size     elementos por página
     * @param status   filtro opcional de estado: "pending", "completed", "cancelled",
     *                 "historical", "all"
     *                 o un valor exacto de {@link OrdenStatus}
     * @param search   filtro de texto libre
     * @param vendedor filtro por nombre de usuario de vendedor
     * @param cliente  filtro por nombre de cliente
     */
    Page<OrderResponse> findAllPaginated(int page, int size, String status, String search, String vendedor, String cliente, String sortBy, String sortOrder);

    // 🔹 VENDEDOR (solo sus órdenes)
    List<OrderResponse> findMyOrders(String username);

    OrderResponse findMyOrderById(UUID id, String username);

    /**
     * Lista paginada de órdenes del vendedor autenticado.
     *
     * @param username    usuario del vendedor
     * @param page        número de página (0-based)
     * @param size        elementos por página
     * @param statusGroup "pending"
     *                    (PENDIENTE+CONFIRMADO+PENDING_PROMOTION_COMPLETION) o
     *                    "completed" (COMPLETADO)
     * @param search      filtro de texto libre
     * @param cliente     filtro por nombre de cliente
     */
    Page<OrderResponse> findMyOrdersPaginated(String username, int page, int size, String statusGroup, String search, String cliente);

    OrderCreationResult updateOrder(UUID orderId, OrderRequestDto request);

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
     * NUEVO: Eliminar un item/promoción de una orden
     * Usado para eliminar promociones individuales (por promotionInstanceId)
     */
    OrderResponse deleteOrderItem(UUID orderId, UUID itemId);

    /**
     * Crear factura histórica para cuadre de caja
     * (Solo Owner - para facturas anteriores al sistema)
     */
    OrderResponse createHistoricalInvoice(CreateHistoricalInvoiceRequest request, String ownerUsername);

    /**
     * Actualizar factura histórica (Solo Owner/Admin)
     * Permite editar TODO (fecha, total, cliente, etc) incluso si está COMPLETADO
     */
    OrderResponse updateHistoricalInvoice(UUID orderId, CreateHistoricalInvoiceRequest request, String username);

    /**
     * Agregar promociones adicionales a una orden que ya es de tipo promoción.
     * Solo Admin/Owner pueden usar este endpoint.
     * No elimina las promociones existentes, solo agrega nuevas instancias.
     */
    OrderResponse addPromotionsToOrder(UUID orderId, List<UUID> promotionIds);

}
