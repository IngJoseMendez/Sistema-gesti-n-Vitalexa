package org.example.sistema_gestion_vitalexa.controller.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.AdminCreateClientRequest;
import org.example.sistema_gestion_vitalexa.dto.ClientResponse;
import org.example.sistema_gestion_vitalexa.dto.VendedorSimpleDTO;
import org.example.sistema_gestion_vitalexa.service.ClientService;
import org.example.sistema_gestion_vitalexa.service.ClientExportService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class ClientAdminController {

    private final ClientService clientService;
    private final ClientExportService clientExportService;

    @GetMapping
    public ResponseEntity<List<ClientResponse>> getAllClients() {
        List<ClientResponse> clients = clientService.findAll();
        return ResponseEntity.ok(clients);
    }

    // List all vendedores for dropdown
    @GetMapping("/vendedores")
    public ResponseEntity<List<VendedorSimpleDTO>> getVendedores() {
        return ResponseEntity.ok(clientService.getVendedores());
    }

    // Get clients for a specific vendedor
    @GetMapping("/seller/{id}")
    public ResponseEntity<List<ClientResponse>> getClientsByVendedor(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(clientService.findByVendedorId(id));
    }

    // Create client for a specific vendedor
    @PostMapping
    public ResponseEntity<ClientResponse> createForVendedor(
            @Valid @RequestBody AdminCreateClientRequest request,
            Authentication auth) {
        ClientResponse response = clientService.createForVendedor(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable java.util.UUID id,
            @RequestBody AdminCreateClientRequest request) {
        return ResponseEntity.ok(clientService.updateForAdmin(id, request));
    }

    // Export clients by vendedor to Excel
    @GetMapping("/export/excel/seller/{vendedorId}")
    public ResponseEntity<byte[]> exportClientsByVendedor(@PathVariable java.util.UUID vendedorId) {
        byte[] excelBytes = clientExportService.exportClientsByVendedor(vendedorId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("clientes_vendedora_" + LocalDate.now() + ".xlsx")
                        .build());
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    // Export clients by address keyword (route) to Excel
    @GetMapping("/export/excel/route")
    public ResponseEntity<byte[]> exportClientsByAddress(@RequestParam String keyword) {
        byte[] excelBytes = clientExportService.exportClientsByAddress(keyword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("clientes_ruta_" + keyword + "_" + LocalDate.now() + ".xlsx")
                        .build());
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    /**
     * Export ALL clients to Excel (general export)
     */
    @GetMapping("/export/excel/all")
    public ResponseEntity<byte[]> exportAllClientsToExcel() {
        byte[] excelBytes = clientExportService.exportAllClients();

        String filename = "todos_los_clientes_" + LocalDate.now() + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename)
                        .build());
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }
}
