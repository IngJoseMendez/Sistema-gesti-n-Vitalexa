package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.entity.SpecialProduct;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.repository.SpecialProductRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/vendedor/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
public class ProductVendedorController {

    private final ProductService productService;
    private final SpecialProductRepository specialProductRepository;
    private final UserRepository userRepository;

    @GetMapping
    public List<ProductResponse> findAllActive(Authentication authentication) {
        // Usar Map para evitar duplicados si por alguna razón las queries retornan
        // repetidos
        java.util.Map<java.util.UUID, ProductResponse> uniqueProducts = new java.util.LinkedHashMap<>();

        // 1. Productos regulares
        List<ProductResponse> regularProducts = productService.findAllActive();
        for (ProductResponse p : regularProducts) {
            uniqueProducts.put(p.id(), p);
        }

        // 2. Productos especiales asignados al vendedor autenticado
        User vendor = userRepository.findByUsername(authentication.getName())
                .orElse(null);
        if (vendor != null) {
            List<SpecialProduct> specialProducts = specialProductRepository
                    .findActiveByVendorId(vendor.getId());
            for (SpecialProduct sp : specialProducts) {
                // Si ya existe (aunque IDs son UUIDs random, es buena práctica), se sobrescribe
                // o ignora
                // Aquí solo nos interesa que no aparezca dos veces el MISMO special product
                uniqueProducts.put(sp.getId(), toProductResponse(sp));
            }
        }

        return new ArrayList<>(uniqueProducts.values());
    }

    /**
     * Convierte un SpecialProduct a ProductResponse para unificación en el catálogo
     */
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
