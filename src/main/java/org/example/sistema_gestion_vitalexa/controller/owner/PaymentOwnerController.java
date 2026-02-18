package org.example.sistema_gestion_vitalexa.controller.owner;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreatePaymentRequest;
import org.example.sistema_gestion_vitalexa.dto.PaymentResponse;
import org.example.sistema_gestion_vitalexa.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller para gestión de pagos/abonos
 * - Lectura (GET): Owner, Admin y Vendedor
 * - Escritura (POST/PUT/DELETE): solo Owner y Admin
 */
@RestController
@RequestMapping("/api/owner/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'VENDEDOR')")
public class PaymentOwnerController {

    private final PaymentService paymentService;

    /**
     * Registrar un nuevo pago/abono para una orden
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<PaymentResponse> registerPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication auth) {
        PaymentResponse response = paymentService.registerPayment(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Obtener los pagos de una orden específica
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsByOrderId(orderId));
    }

    /**
     * Obtener los pagos activos de una orden específica (excluye anulados)
     */
    @GetMapping("/order/{orderId}/active")
    public ResponseEntity<List<PaymentResponse>> getActivePaymentsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getActivePaymentsByOrderId(orderId));
    }

    /**
     * Obtener un pago específico por ID (incluye auditoría)
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentById(paymentId));
    }

    /**
     * Anular un pago (soft delete con auditoría)
     */
    @PutMapping("/{paymentId}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<PaymentResponse> cancelPayment(
            @PathVariable UUID paymentId,
            @RequestParam(required = false) String reason,
            Authentication auth) {
        PaymentResponse response = paymentService.cancelPayment(paymentId, reason, auth.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Restaurar un pago anulado
     */
    @PutMapping("/{paymentId}/restore")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<PaymentResponse> restorePayment(
            @PathVariable UUID paymentId,
            Authentication auth) {
        PaymentResponse response = paymentService.restorePayment(paymentId, auth.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Anular un pago (DELETE - deprecado, usar PUT /cancel)
     * 
     * @deprecated Usar cancelPayment en su lugar
     */
    @DeleteMapping("/{paymentId}")
    @Deprecated
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Void> deletePayment(
            @PathVariable UUID paymentId,
            Authentication auth) {
        paymentService.deletePayment(paymentId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
