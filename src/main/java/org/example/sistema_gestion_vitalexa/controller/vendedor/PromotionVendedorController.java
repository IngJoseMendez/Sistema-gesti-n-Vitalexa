package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.PromotionResponse;
import org.example.sistema_gestion_vitalexa.service.PromotionService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/vendedor/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
@Slf4j
public class PromotionVendedorController {

    private final PromotionService promotionService;

    /**
     * GET /api/vendedor/promotions - Ver promociones válidas
     * Usa FETCH JOIN optimizado para cargar todo en una sola query.
     * Cache de 2 minutos para reducir peticiones con internet lento.
     */
    @GetMapping
    public ResponseEntity<List<PromotionResponse>> findValidPromotions() {
        log.info("Vendedor listando promociones válidas");
        List<PromotionResponse> promotions = promotionService.findValidPromotionsEager();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(2, TimeUnit.MINUTES).noTransform())
                .body(promotions);
    }
}
