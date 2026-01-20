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
 * Controller para gestión de pagos/abonos (solo Owner)
 */
@RestController
@RequestMapping("/api/owner/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class PaymentOwnerController {

    private final PaymentService paymentService;

    /**
     * Registrar un nuevo pago/abono para una orden
     */
    @PostMapping
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
     * Anular un pago
     */
    @DeleteMapping("/{paymentId}")
    public ResponseEntity<Void> deletePayment(
            @PathVariable UUID paymentId,
            Authentication auth) {
        paymentService.deletePayment(paymentId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
