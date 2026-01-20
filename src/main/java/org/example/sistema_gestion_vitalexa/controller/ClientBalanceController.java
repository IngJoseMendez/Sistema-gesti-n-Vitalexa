package org.example.sistema_gestion_vitalexa.controller;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ClientBalanceDTO;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.service.ClientBalanceService;
import org.example.sistema_gestion_vitalexa.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Controller para panel de saldos de clientes.
 * Visibilidad según rol:
 * - Owner/Admin: Todos los clientes
 * - Vendedor: Solo sus clientes asignados
 * - Cliente: Solo su propia información
 */
@RestController
@RequestMapping("/api/balances")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN','VENDEDOR','CLIENTE')")
public class ClientBalanceController {

    private final ClientBalanceService clientBalanceService;
    private final UserService userService;

    /**
     * Obtener saldos de clientes según el rol del usuario
     */
    @GetMapping
    public ResponseEntity<List<ClientBalanceDTO>> getClientBalances(
            @RequestParam(required = false) UUID vendedorId,
            Authentication auth) {
        String username = auth.getName();
        Role userRole = userService.getUserRole(username);

        List<ClientBalanceDTO> balances;

        switch (userRole) {
            case OWNER, ADMIN -> {
                // Owner y Admin pueden ver todos o filtrar por vendedora
                if (vendedorId != null) {
                    balances = clientBalanceService.getClientBalancesByVendedor(vendedorId);
                } else {
                    balances = clientBalanceService.getAllClientBalances();
                }
            }
            case VENDEDOR -> {
                // Vendedor solo ve sus clientes asignados
                balances = clientBalanceService.getMyClientBalances(username);
            }
            default -> {
                balances = List.of();
            }
        }

        return ResponseEntity.ok(balances);
    }

    /**
     * Obtener saldo de un cliente específico
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<ClientBalanceDTO> getClientBalance(@PathVariable UUID clientId) {
        return ResponseEntity.ok(clientBalanceService.getClientBalance(clientId));
    }

    /**
     * Establecer saldo inicial de un cliente (solo Owner, una sola vez)
     */
    @PutMapping("/client/{clientId}/initial-balance")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> setInitialBalance(
            @PathVariable UUID clientId,
            @RequestParam BigDecimal amount,
            Authentication auth) {
        clientBalanceService.setInitialBalance(clientId, amount, auth.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Establecer tope de crédito de un cliente (solo Owner)
     */
    @PutMapping("/client/{clientId}/credit-limit")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> setCreditLimit(
            @PathVariable UUID clientId,
            @RequestParam BigDecimal amount,
            Authentication auth) {
        clientBalanceService.setCreditLimit(clientId, amount, auth.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Eliminar tope de crédito de un cliente (solo Owner)
     */
    @DeleteMapping("/client/{clientId}/credit-limit")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> removeCreditLimit(
            @PathVariable UUID clientId,
            Authentication auth) {
        clientBalanceService.removeCreditLimit(clientId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
