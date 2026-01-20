package org.example.sistema_gestion_vitalexa.controller.owner;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ApplyDiscountRequest;
import org.example.sistema_gestion_vitalexa.dto.DiscountResponse;
import org.example.sistema_gestion_vitalexa.service.DiscountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller para que el Owner gestione descuentos (revocar, agregar
 * adicionales)
 */
@RestController
@RequestMapping("/api/owner/discounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class DiscountOwnerController {

    private final DiscountService discountService;

    /**
     * Revocar un descuento
     */
    @PutMapping("/{discountId}/revoke")
    public ResponseEntity<DiscountResponse> revokeDiscount(
            @PathVariable UUID discountId,
            Authentication auth) {
        DiscountResponse response = discountService.revokeDiscount(discountId, auth.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Aplicar un descuento adicional del Owner
     */
    @PostMapping
    public ResponseEntity<DiscountResponse> applyOwnerDiscount(
            @Valid @RequestBody ApplyDiscountRequest request,
            Authentication auth) {
        DiscountResponse response = discountService.applyOwnerDiscount(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
