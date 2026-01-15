package org.example.sistema_gestion_vitalexa.controller.cliente;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequestMapping("/api/cliente/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class ProductClienteController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> findAllActive() {
        return productService.findAllActive();
    }

    @GetMapping("/page")
    public Page<ProductResponse> findAllActivePaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean inStock
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nombre").ascending());
        // PageRequest.of(page,size,sort) es el patrón estándar [web:253]

        if (Boolean.TRUE.equals(inStock)) {
            return productService.findAllActiveInStock(pageable);
        }

        if (q != null && !q.isBlank()) {
            return productService.searchActive(q, pageable);
        }

        return productService.findAllActive(pageable);
    }
}
