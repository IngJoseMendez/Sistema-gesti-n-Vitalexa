package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.PromotionResponse;
import org.example.sistema_gestion_vitalexa.service.PromotionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vendedor/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
@Slf4j
public class PromotionVendedorController {

    private final PromotionService promotionService;

    /**
     * GET /api/vendedor/promotions - Ver promociones válidas
     * (activas y dentro del período de validez)
     */
    @GetMapping
    public ResponseEntity<List<PromotionResponse>> findValidPromotions() {
        log.info("Vendedor listando promociones válidas");
        List<PromotionResponse> promotions = promotionService.findValidPromotions();
        return ResponseEntity.ok(promotions);
    }
}
