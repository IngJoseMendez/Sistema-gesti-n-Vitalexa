package org.example.sistema_gestion_vitalexa.controller.owner;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ClientResponse;
import org.example.sistema_gestion_vitalexa.dto.CreateHistoricalInvoiceRequest;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.service.ClientService;
import org.example.sistema_gestion_vitalexa.service.OrdenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para que el Owner agregue facturas históricas
 * (para cuadre de caja con facturas anteriores al sistema)
 */
@RestController
@RequestMapping("/api/owner/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class InvoiceOwnerController {

    private final OrdenService ordenService;
    private final ClientService clientService;

    /**
     * GET /api/owner/invoices/clients
     * Obtener lista de TODOS los clientes disponibles del sistema
     * (no solo los del Owner, sino todos para poder vincularlos a facturas
     * históricas)
     */
    @GetMapping("/clients")
    public ResponseEntity<List<ClientResponse>> getAvailableClients() {
        // Obtener TODOS los clientes del sistema, sin filtrar por vendedor
        List<ClientResponse> clients = clientService.findAll();
        return ResponseEntity.ok(clients);
    }

    /**
     * POST /api/owner/invoices
     * Crear una factura histórica para cuadre de caja
     *
     * Datos obligatorios:
     * - invoiceNumber: Número único de factura
     * - fecha: Fecha de la factura
     * - totalValue: Monto total de la factura
     * - dueAmount: Monto que debe el cliente
     *
     * Datos opcionales:
     * - clientId: ID del cliente registrado (RECOMENDADO)
     * - clientName: Nombre del cliente (si no está registrado)
     * - clientPhone: Teléfono del cliente
     * - clientEmail: Email del cliente
     * - clientAddress: Dirección del cliente
     * - notes: Notas adicionales
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createHistoricalInvoice(
            @Valid @RequestBody CreateHistoricalInvoiceRequest request,
            Authentication auth) {

        OrderResponse response = ordenService.createHistoricalInvoice(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/owner/invoices/{id}
     * Actualizar una factura histórica (solo OWNER)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OrderResponse> updateHistoricalInvoice(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody CreateHistoricalInvoiceRequest request,
            Authentication auth) {

        OrderResponse response = ordenService.updateHistoricalInvoice(id, request, auth.getName());
        return ResponseEntity.ok(response);
    }
}
