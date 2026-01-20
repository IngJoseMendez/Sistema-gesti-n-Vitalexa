package org.example.sistema_gestion_vitalexa.controller.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ApplyDiscountRequest;
import org.example.sistema_gestion_vitalexa.dto.DiscountResponse;
import org.example.sistema_gestion_vitalexa.enums.DiscountType;
import org.example.sistema_gestion_vitalexa.service.DiscountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller para gestión de descuentos (Admin aplica, Owner también puede ver)
 */
@RestController
@RequestMapping("/api/admin/discounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class DiscountAdminController {

    private final DiscountService discountService;

    /**
     * Aplicar descuento del 10%
     */
    @PostMapping("/order/{orderId}/apply-10")
    public ResponseEntity<DiscountResponse> applyDiscount10(
            @PathVariable UUID orderId,
            Authentication auth) {
        DiscountResponse response = discountService.applyPresetDiscount(orderId, DiscountType.ADMIN_10, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Aplicar descuento del 12%
     */
    @PostMapping("/order/{orderId}/apply-12")
    public ResponseEntity<DiscountResponse> applyDiscount12(
            @PathVariable UUID orderId,
            Authentication auth) {
        DiscountResponse response = discountService.applyPresetDiscount(orderId, DiscountType.ADMIN_12, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Aplicar descuento del 15%
     */
    @PostMapping("/order/{orderId}/apply-15")
    public ResponseEntity<DiscountResponse> applyDiscount15(
            @PathVariable UUID orderId,
            Authentication auth) {
        DiscountResponse response = discountService.applyPresetDiscount(orderId, DiscountType.ADMIN_15, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Aplicar descuento personalizado
     */
    @PostMapping("/custom")
    public ResponseEntity<DiscountResponse> applyCustomDiscount(
            @Valid @RequestBody ApplyDiscountRequest request,
            Authentication auth) {
        DiscountResponse response = discountService.applyCustomDiscount(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Ver descuentos de una orden
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<DiscountResponse>> getDiscountsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(discountService.getDiscountsByOrderId(orderId));
    }
}
