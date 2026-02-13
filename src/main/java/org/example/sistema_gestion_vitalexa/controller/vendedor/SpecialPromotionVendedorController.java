package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.SpecialPromotionResponse;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.SpecialPromotionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendedor/special-promotions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('VENDEDOR', 'ADMIN', 'OWNER')")
public class SpecialPromotionVendedorController {

    private final SpecialPromotionService specialPromotionService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<SpecialPromotionResponse>> findMySpecialPromotions(
            Authentication authentication, Pageable pageable) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(specialPromotionService.findByVendor(user.getId(), pageable));
    }
}
