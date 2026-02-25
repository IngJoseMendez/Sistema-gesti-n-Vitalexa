package org.example.sistema_gestion_vitalexa.controller.admin;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.AddAssortmentItemRequest;
import org.example.sistema_gestion_vitalexa.dto.OrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateEtaRequest;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.service.InvoiceService;
import org.example.sistema_gestion_vitalexa.service.OrdenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class OrderAdminController {

    private final OrdenService ordenService;
    private final InvoiceService invoiceService;

    @GetMapping
    public List<OrderResponse> findAll() {
        return ordenService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable UUID id) {

        return ResponseEntity.ok(ordenService.findById(id));
    }

    /**
     * PATCH /api/admin/orders/{id}/status
     * ADMIN y OWNER pueden cambiar estado (COMPLETADO, PENDIENTE, EN_PROCESO, etc.)
     * SOLO OWNER puede poner estado ANULADA — usar el endpoint /annul para anular con motivo.
     */
    @PatchMapping("/{id}/status")
    public OrderResponse changeStatus(
            @PathVariable UUID id,
            @RequestParam OrdenStatus status,
            Authentication authentication) {
        boolean isOwner = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));
        if (status == OrdenStatus.ANULADA && !isOwner) {
            throw new BusinessExeption("Solo el Owner puede anular ventas. Usa el endpoint /annul con un motivo.");
        }
        return ordenService.cambiarEstadoOrden(id, status);
    }

    /**
     * PUT /api/admin/orders/{id}
     * Editar el contenido de una orden (items, totales).
     * ADMIN y OWNER pueden editar.
     */
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable UUID id,
            @RequestBody OrderRequestDto request) {
        OrderResponse response = ordenService.updateOrder(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generar PDF de la orden (para vendedor/empacador)
     */
    @GetMapping("/{id}/invoice/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<byte[]> downloadOrderInvoice(@PathVariable UUID id) {
        byte[] pdfBytes = invoiceService.generateOrderInvoicePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "orden_" + id.toString().substring(0, 8) + ".pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    /**
     * Visualizar PDF de la orden en el navegador
     */
    @GetMapping("/{id}/invoice/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<byte[]> previewOrderInvoice(@PathVariable UUID id) {
        byte[] pdfBytes = invoiceService.generateOrderInvoicePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline",
                "orden_" + id.toString().substring(0, 8) + ".pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    /**
     * POST /api/admin/orders/{orderId}/promotions/{promotionId}/assortment
     * Agregar productos surtidos para completar una promoción
     */
    @PostMapping("/{orderId}/promotions/{promotionId}/assortment")
    public ResponseEntity<Void> addPromotionAssortment(
            @PathVariable UUID orderId,
            @PathVariable UUID promotionId,
            @RequestBody List<AddAssortmentItemRequest> items) {
        ordenService.addPromotionAssortment(orderId, promotionId, items);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/admin/orders/{orderId}/items/{itemId}/eta
     * Actualizar ETA de un item
     */
    @PatchMapping("/{orderId}/items/{itemId}/eta")
    public ResponseEntity<Void> updateItemEta(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @RequestBody UpdateEtaRequest request) {

        ordenService.updateItemEta(orderId, itemId, request.eta(), request.note());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/admin/orders/{orderId}/items/{itemId}
     * Eliminar un item (promoción/bonificado) de una orden
     */
    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderResponse> deleteOrderItem(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId) {
        OrderResponse response = ordenService.deleteOrderItem(orderId, itemId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/orders
     * Crear orden (Admin/Owner puede especificar sellerId en el body)
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderRequestDto request,
            org.springframework.security.core.Authentication authentication) {
        OrderResponse response = ordenService.createOrder(request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * POST /api/admin/orders/{id}/annul
     * Anular orden con motivo.
     * - OWNER: puede anular cualquier orden (incluso COMPLETADAS).
     * - ADMIN: solo puede anular órdenes que NO estén COMPLETADAS.
     */
    @PostMapping("/{id}/annul")
    public ResponseEntity<Void> annulOrder(
            @PathVariable UUID id,
            @RequestParam String reason,
            Authentication authentication) {

        boolean isOwner = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));

        if (!isOwner) {
            // El Admin no puede anular órdenes completadas
            OrderResponse order = ordenService.findById(id);
            if ("COMPLETADO".equals(order.estado())) {
                throw new BusinessExeption("Solo el Owner puede anular una orden que ya está completada");
            }
        }

        ordenService.annulOrder(id, reason);
        return ResponseEntity.noContent().build();
    }
}
