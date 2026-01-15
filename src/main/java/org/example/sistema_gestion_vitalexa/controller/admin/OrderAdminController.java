package org.example.sistema_gestion_vitalexa.controller.admin;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.OrderRequestDto;
import org.example.sistema_gestion_vitalexa.dto.OrderResponse;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.service.InvoiceService;
import org.example.sistema_gestion_vitalexa.service.OrdenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class OrderAdminController {

    private final OrdenService ordenService;
    private final InvoiceService invoiceService;

    @GetMapping
    public List<OrderResponse> findAll() {
        return ordenService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable UUID id) {

        return ResponseEntity.ok(ordenService.findById(id));
    }

    @PatchMapping("/{id}/status")
    public OrderResponse changeStatus(
            @PathVariable UUID id,
            @RequestParam OrdenStatus status
    ) {
        return ordenService.cambiarEstadoOrden(id, status);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable UUID id,
            @RequestBody OrderRequestDto request
    ) {
        OrderResponse response = ordenService.updateOrder(id, request);
        return ResponseEntity.ok(response);
    }



    /**
     * Generar PDF de la orden (para vendedor/empacador)
     */
    @GetMapping("/{id}/invoice/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<byte[]> downloadOrderInvoice(@PathVariable UUID id) {
        byte[] pdfBytes = invoiceService.generateOrderInvoicePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "orden_" + id.toString().substring(0, 8) + ".pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    /**
     * Visualizar PDF de la orden en el navegador
     */
    @GetMapping("/{id}/invoice/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<byte[]> previewOrderInvoice(@PathVariable UUID id) {
        byte[] pdfBytes = invoiceService.generateOrderInvoicePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline",
                "orden_" + id.toString().substring(0, 8) + ".pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

}
