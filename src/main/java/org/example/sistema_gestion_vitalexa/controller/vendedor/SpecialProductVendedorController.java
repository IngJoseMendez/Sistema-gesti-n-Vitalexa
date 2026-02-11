package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.SpecialProductResponse;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.SpecialProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendedor/special-products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
public class SpecialProductVendedorController {

    private final SpecialProductService specialProductService;
    private final UserRepository userRepository;

    /**
     * Lista los productos especiales asignados al vendedor autenticado.
     */
    @GetMapping
    public ResponseEntity<Page<SpecialProductResponse>> findMySpecialProducts(
            Authentication authentication, Pageable pageable) {
        User vendor = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(specialProductService.findByVendor(vendor.getId(), pageable));
    }
}
