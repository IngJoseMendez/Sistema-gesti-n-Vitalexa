package org.example.sistema_gestion_vitalexa.controller;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ReembolsoRequest;
import org.example.sistema_gestion_vitalexa.dto.ReembolsoResponse;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.repository.ProductRepository;
import org.example.sistema_gestion_vitalexa.service.ReembolsoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/empacador")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPACADOR')")
public class EmpacadorController {

    private final ProductRepository productRepository;
    private final ReembolsoService reembolsoService;

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getProductosDisponibles() {
        List<Product> productos = productRepository.findByActiveTrue()
                .stream()
                .filter(p -> p.getStock() > 0)
                .toList();
        return ResponseEntity.ok(productos);
    }

    @PostMapping("/reembolsos")
    public ResponseEntity<ReembolsoResponse> crearReembolso(
            @RequestBody ReembolsoRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        ReembolsoResponse response = reembolsoService.crearReembolso(request, username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reembolsos")
    public ResponseEntity<List<ReembolsoResponse>> getMisReembolsos(Authentication authentication) {
        String username = authentication.getName();
        List<ReembolsoResponse> reembolsos = reembolsoService.obtenerReembolsosPorEmpacador(username);
        return ResponseEntity.ok(reembolsos);
    }
}
