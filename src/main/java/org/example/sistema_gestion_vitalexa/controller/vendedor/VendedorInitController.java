package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.dto.PromotionResponse;
import org.example.sistema_gestion_vitalexa.dto.SpecialProductResponse;
import org.example.sistema_gestion_vitalexa.dto.SpecialPromotionResponse;
import org.example.sistema_gestion_vitalexa.dto.VendedorInitDTO;
import org.example.sistema_gestion_vitalexa.entity.SpecialProduct;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.repository.SpecialProductRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.example.sistema_gestion_vitalexa.service.PromotionService;
import org.example.sistema_gestion_vitalexa.service.SpecialPromotionService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Endpoint de inicio optimizado para vendedoras.
 *
 * PROBLEMA RESUELTO: Con internet lento, el frontend hacía 3 o más peticiones
 * HTTP separadas (productos, promociones, promo-especiales) y alguna fallaba
 * o tardaba tanto que la página quedaba incompleta.
 *
 * SOLUCIÓN: UNA SOLA petición GET /api/vendedor/init trae todo lo necesario
 * para mostrar el catálogo y las promociones.
 *
 * BENEFICIOS ADICIONALES:
 * - Header Cache-Control: el navegador guarda la respuesta 2 minutos, así si
 *   la vendedora recarga la página no tiene que volver a pedir los datos.
 * - El backend usa FETCH JOIN (una sola query a la BD para las promociones),
 *   reduciendo la latencia del servidor también.
 */
@RestController
@RequestMapping("/api/vendedor/init")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
@Slf4j
public class VendedorInitController {

    private final ProductService productService;
    private final PromotionService promotionService;
    private final SpecialPromotionService specialPromotionService;
    private final UserRepository userRepository;
    private final SpecialProductRepository specialProductRepository;

    /**
     * GET /api/vendedor/init
     *
     * Devuelve en una sola respuesta:
     *  - productos (regulares + especiales del vendedor)
     *  - promociones válidas (cargadas con FETCH JOIN, una sola query)
     *  - promociones especiales del vendedor
     *
     * Cache-Control: max-age=120 (2 minutos en el navegador)
     */
    @GetMapping
    public ResponseEntity<VendedorInitDTO> init(Authentication authentication) {
        long inicio = System.currentTimeMillis();
        String username = authentication.getName();
        log.info("[INIT] Cargando datos de inicio para vendedor: {}", username);

        // --- 1. Productos (regulares + especiales del vendedor, sin duplicados) ---
        Map<UUID, ProductResponse> productosMap = new LinkedHashMap<>();

        List<ProductResponse> regulares = productService.findAllActive();
        for (ProductResponse p : regulares) {
            productosMap.put(p.id(), p);
        }

        User vendor = userRepository.findByUsername(username).orElse(null);
        if (vendor != null) {
            List<SpecialProduct> especiales = specialProductRepository.findActiveByVendorId(vendor.getId());
            for (SpecialProduct sp : especiales) {
                productosMap.put(sp.getId(), toProductResponse(sp));
            }
        }

        // --- 2. Promociones válidas (query optimizada con FETCH JOIN) ---
        List<PromotionResponse> promociones = promotionService.findValidPromotionsEager();

        // --- 3. Promociones especiales del vendedor ---
        List<SpecialPromotionResponse> promosEspeciales = new ArrayList<>();
        if (vendor != null) {
            promosEspeciales = specialPromotionService.findByVendor(vendor.getId());
        }

        VendedorInitDTO response = new VendedorInitDTO(
                new ArrayList<>(productosMap.values()),
                promociones,
                promosEspeciales
        );

        long ms = System.currentTimeMillis() - inicio;
        log.info("[INIT] Datos de inicio listos en {}ms → {} productos, {} promo, {} promoEspeciales",
                ms, response.productos().size(), response.promociones().size(),
                response.promocionesEspeciales().size());

        return ResponseEntity.ok()
                // 2 minutos de caché en el navegador: si la señal falla,
                // el browser usa la versión guardada sin pedir al servidor
                .cacheControl(CacheControl.maxAge(2, TimeUnit.MINUTES).noTransform())
                .body(response);
    }

    private ProductResponse toProductResponse(SpecialProduct sp) {
        return new ProductResponse(
                sp.getId(),
                sp.getNombre(),
                sp.getDescripcion(),
                sp.getPrecio(),
                sp.getEffectiveStock(),
                sp.getImageUrl(),
                sp.isActive(),
                sp.getReorderPoint(),
                sp.getTag() != null ? sp.getTag().getId() : null,
                sp.getTag() != null ? sp.getTag().getName() : null,
                0,
                true,
                sp.getId());
    }
}

