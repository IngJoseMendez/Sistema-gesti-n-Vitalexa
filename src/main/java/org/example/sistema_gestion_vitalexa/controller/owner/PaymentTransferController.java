package org.example.sistema_gestion_vitalexa.controller.owner;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreatePaymentTransferRequest;
import org.example.sistema_gestion_vitalexa.dto.PaymentTransferResponse;
import org.example.sistema_gestion_vitalexa.dto.RevokePaymentTransferRequest;
import org.example.sistema_gestion_vitalexa.service.PaymentTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints de transferencia de pagos entre vendedores.
 * Solo accesible por el Owner.
 */
@RestController
@RequestMapping("/api/owner/payment-transfers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class PaymentTransferController {

    private final PaymentTransferService transferService;

    /**
     * POST /api/owner/payment-transfers
     * Crea una nueva transferencia de pago hacia otro vendedor.
     */
    @PostMapping
    public ResponseEntity<PaymentTransferResponse> createTransfer(
            @RequestBody CreatePaymentTransferRequest request,
            Authentication authentication) {
        PaymentTransferResponse response = transferService.createTransfer(request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * POST /api/owner/payment-transfers/{id}/revoke
     * Revoca una transferencia existente.
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<PaymentTransferResponse> revokeTransfer(
            @PathVariable UUID id,
            @RequestBody RevokePaymentTransferRequest request,
            Authentication authentication) {
        PaymentTransferResponse response = transferService.revokeTransfer(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/owner/payment-transfers/payment/{paymentId}
     * Lista todas las transferencias realizadas sobre un pago.
     */
    @GetMapping("/payment/{paymentId}")
    public List<PaymentTransferResponse> getTransfersByPayment(@PathVariable UUID paymentId) {
        return transferService.getTransfersByPayment(paymentId);
    }

    /**
     * GET /api/owner/payment-transfers/payment/{paymentId}/available
     * Retorna el saldo disponible para transferir de un pago.
     */
    @GetMapping("/payment/{paymentId}/available")
    public BigDecimal getAvailableAmount(@PathVariable UUID paymentId) {
        return transferService.getAvailableAmountForPayment(paymentId);
    }

    /**
     * GET /api/owner/payment-transfers/origin/{vendedorId}
     * Lista transferencias donde este vendedor es el origen.
     */
    @GetMapping("/origin/{vendedorId}")
    public List<PaymentTransferResponse> getByOrigin(@PathVariable UUID vendedorId) {
        return transferService.getTransfersByOriginVendedor(vendedorId);
    }

    /**
     * GET /api/owner/payment-transfers/dest/{vendedorId}
     * Lista transferencias donde este vendedor es el destino.
     */
    @GetMapping("/dest/{vendedorId}")
    public List<PaymentTransferResponse> getByDest(@PathVariable UUID vendedorId) {
        return transferService.getTransfersByDestVendedor(vendedorId);
    }
}
