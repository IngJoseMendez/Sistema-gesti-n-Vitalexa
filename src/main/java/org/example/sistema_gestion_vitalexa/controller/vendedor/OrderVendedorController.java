package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.OrderCreationResult;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.service.OrdenService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.example.sistema_gestion_vitalexa.dto.OrderRequestDto;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendedor/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
public class OrderVendedorController {

    private final OrdenService ordenService;

    // 🔹 Crear venta — retorna OrderCreationResult para indicar si se hizo split S/R
    @PostMapping
    public OrderCreationResult create(
            @Valid @RequestBody OrderRequestDto request,
            Authentication authentication) {
        String username = authentication.getName();
        return ordenService.createOrder(request, username);
    }

    // 🔹 Ver MIS órdenes
    @GetMapping("/my")
    public List<OrderResponse> findMyOrders(Authentication authentication) {
        String username = authentication.getName();
        return ordenService.findMyOrders(username);
    }

    /**
     * GET /api/vendedor/orders/my/paginated?statusGroup=pending&page=0&size=20
     * Retorna órdenes del vendedor paginadas con metadatos.
     *
     * @param statusGroup "pending" o "completed"
     * @param page        número de página (0-based)
     * @param size        elementos por página (máximo 50)
     */
    @GetMapping("/my/paginated")
    public ResponseEntity<Page<OrderResponse>> findMyOrdersPaginated(
            @RequestParam(defaultValue = "pending") String statusGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String cliente,
            Authentication authentication) {
        int safeSize = Math.min(size, 50);
        Page<OrderResponse> resultado = ordenService.findMyOrdersPaginated(
                authentication.getName(), page, safeSize, statusGroup, search, cliente);
        return ResponseEntity.ok(resultado);
    }

    // 🔹 Ver detalle de MI orden
    @GetMapping("/{id}")
    public OrderResponse findMyOrderById(
            @PathVariable UUID id,
            Authentication authentication) {
        return ordenService.findMyOrderById(id, authentication.getName());
    }
}
