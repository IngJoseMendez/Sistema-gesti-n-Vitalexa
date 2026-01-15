package org.example.sistema_gestion_vitalexa.controller.cliente;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CustomerOrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.service.CustomerOrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cliente/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class OrderClienteController {

    private final CustomerOrderService service;

    @PostMapping
    public OrderResponse create(@RequestBody CustomerOrderRequestDto request, Principal principal) {
        return service.create(principal.getName(), request);
    }

    @GetMapping
    public List<OrderResponse> myOrders(Principal principal) {
        return service.myOrders(principal.getName());
    }

    @GetMapping("/{id}")
    public OrderResponse myOrderDetail(@PathVariable UUID id, Principal principal) {
        return service.myOrderDetail(principal.getName(), id);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id, Principal principal) {
        return service.cancel(principal.getName(), id);
    }

    @PostMapping("/{id}/reorder")
    public OrderResponse reorder(@PathVariable UUID id, Principal principal) {
        return service.reorder(principal.getName(), id);
    }
}



