package org.example.sistema_gestion_vitalexa.controller;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ClientBalanceDTO;
import org.example.sistema_gestion_vitalexa.dto.OrderPendingDTO;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.service.ClientBalanceService;
import org.example.sistema_gestion_vitalexa.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
            case OWNER:
            case ADMIN:
                // Owner y Admin pueden ver todos o filtrar por vendedora
                if (vendedorId != null) {
                    balances = clientBalanceService.getClientBalancesByVendedor(vendedorId);
                } else {
                    balances = clientBalanceService.getAllClientBalances();
                }
                break;
            case VENDEDOR:
                // Vendedor solo ve sus clientes asignados
                balances = clientBalanceService.getMyClientBalances(username);
                break;
            default:
                balances = List.of();
                break;
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

    /**
     * Agregar Saldo a Favor a un cliente (solo Owner)
     */
    @PutMapping("/client/{clientId}/balance-favor")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> addBalanceFavor(
            @PathVariable UUID clientId,
            @RequestParam BigDecimal amount,
            Authentication auth) {
        clientBalanceService.addBalanceFavor(clientId, amount, auth.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Obtener detalle de facturas pendientes de un cliente con filtros de fecha
     */
    @GetMapping("/client/{clientId}/pending-invoices")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','VENDEDOR')")
    public ResponseEntity<List<OrderPendingDTO>> getPendingInvoices(
            @PathVariable UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(clientBalanceService.getPendingInvoicesByClient(clientId, startDate, endDate));
    }

    /**
     * Obtener días de mora de un cliente
     */
    @GetMapping("/client/{clientId}/days-overdue")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','VENDEDOR')")
    public ResponseEntity<Integer> getDaysOverdue(@PathVariable UUID clientId) {
        return ResponseEntity.ok(clientBalanceService.calculateDaysOverdue(clientId));
    }

    /**
     * Obtener última fecha de pago de un cliente
     */
    @GetMapping("/client/{clientId}/last-payment-date")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','VENDEDOR')")
    public ResponseEntity<LocalDate> getLastPaymentDate(@PathVariable UUID clientId) {
        LocalDate lastDate = clientBalanceService.getLastPaymentDate(clientId);
        return lastDate != null ? ResponseEntity.ok(lastDate) : ResponseEntity.noContent().build();
    }

    /**
     * Obtener facturas pendientes de un cliente específico con filtros de fecha
     */
    @GetMapping("/client/{clientId}/invoices/pending")
    public ResponseEntity<List<OrderPendingDTO>> getPendingInvoicesByClient(
            @PathVariable UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(clientBalanceService.getPendingInvoicesByClient(clientId, startDate, endDate));
    }

    /**
     * Obtener TODAS las facturas de un cliente (pagadas y pendientes) con historial completo de pagos
     */
    @GetMapping("/client/{clientId}/invoices/all")
    public ResponseEntity<List<OrderPendingDTO>> getAllInvoicesByClient(
            @PathVariable UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(clientBalanceService.getAllInvoicesByClient(clientId, startDate, endDate));
    }

    /**
     * Exportar cartera por vendedor a Excel
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportBalanceToExcel(
            @RequestParam(required = false) UUID vendedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyWithDebt,
            Authentication auth) throws IOException {

        byte[] excelData = clientBalanceService.exportBalanceToExcel(
                vendedorId, startDate, endDate, onlyWithDebt, auth.getName());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "cartera-clientes.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }
}
