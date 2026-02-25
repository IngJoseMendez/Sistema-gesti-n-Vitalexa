package org.example.sistema_gestion_vitalexa.controller.owner;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.PayrollExportService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller para descargar reportes de nómina en Excel y PDF.
 * - Exportaciones generales (todos los vendedores): OWNER y ADMIN
 * - Exportaciones por vendedor específico: OWNER, ADMIN y el propio VENDEDOR
 */
@RestController
@RequestMapping("/api/owner/payroll/export")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'VENDEDOR')")
public class PayrollExportController {

    private final PayrollExportService payrollExportService;
    private final UserRepository userRepository;

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    // ─── EXCEL GENERAL (OWNER / ADMIN) ────────────────────────────────────────

    /**
     * GET /api/owner/payroll/export/excel?month=2&year=2026
     * Nómina de TODOS los vendedores — solo OWNER y ADMIN.
     */
    @GetMapping("/excel")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<byte[]> exportAllPayrollsExcel(
            @RequestParam int month,
            @RequestParam int year) {

        byte[] bytes = payrollExportService.exportAllPayrollsToExcel(month, year);
        return buildResponse(bytes, XLSX, "nomina_general_" + month + "_" + year + ".xlsx");
    }

    /**
     * GET /api/owner/payroll/export/pdf?month=2&year=2026
     * Nómina de TODOS los vendedores — solo OWNER y ADMIN.
     */
    @GetMapping("/pdf")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<byte[]> exportAllPayrollsPdf(
            @RequestParam int month,
            @RequestParam int year) {

        byte[] bytes = payrollExportService.exportAllPayrollsToPdf(month, year);
        return buildResponse(bytes, MediaType.APPLICATION_PDF, "nomina_general_" + month + "_" + year + ".pdf");
    }

    // ─── EXCEL / PDF PROPIO (VENDEDOR — sin necesitar su UUID) ───────────────

    /**
     * GET /api/owner/payroll/export/me/excel?month=2&year=2026
     * La vendedora descarga su propio Excel usando solo su token JWT.
     * También accesible por OWNER y ADMIN.
     */
    @GetMapping("/me/excel")
    public ResponseEntity<byte[]> exportMyPayrollExcel(
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {

        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));
        byte[] bytes = payrollExportService.exportVendorPayrollToExcel(currentUser.getId(), month, year);
        String filename = "mi_nomina_" + month + "_" + year + ".xlsx";
        return buildResponse(bytes, XLSX, filename);
    }

    /**
     * GET /api/owner/payroll/export/me/pdf?month=2&year=2026
     * La vendedora descarga su propio PDF usando solo su token JWT.
     * También accesible por OWNER y ADMIN.
     */
    @GetMapping("/me/pdf")
    public ResponseEntity<byte[]> exportMyPayrollPdf(
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {

        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));
        byte[] bytes = payrollExportService.exportVendorPayrollToPdf(currentUser.getId(), month, year);
        String filename = "mi_nomina_" + month + "_" + year + ".pdf";
        return buildResponse(bytes, MediaType.APPLICATION_PDF, filename);
    }

    // ─── EXCEL / PDF POR VENDEDOR (OWNER, ADMIN y el propio VENDEDOR) ─────────

    /**
     * GET /api/owner/payroll/export/{vendedorId}/excel?month=2&year=2026
     * OWNER y ADMIN: cualquier vendedor.
     * VENDEDOR: solo su propio ID.
     */
    @GetMapping("/{vendedorId}/excel")
    public ResponseEntity<byte[]> exportVendorPayrollExcel(
            @PathVariable UUID vendedorId,
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {

        validateVendedorAccess(vendedorId, authentication);
        byte[] bytes = payrollExportService.exportVendorPayrollToExcel(vendedorId, month, year);
        String filename = "nomina_" + vendedorId + "_" + month + "_" + year + ".xlsx";
        return buildResponse(bytes, XLSX, filename);
    }

    /**
     * GET /api/owner/payroll/export/{vendedorId}/pdf?month=2&year=2026
     * OWNER y ADMIN: cualquier vendedor.
     * VENDEDOR: solo su propio ID.
     */
    @GetMapping("/{vendedorId}/pdf")
    public ResponseEntity<byte[]> exportVendorPayrollPdf(
            @PathVariable UUID vendedorId,
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {

        validateVendedorAccess(vendedorId, authentication);
        byte[] bytes = payrollExportService.exportVendorPayrollToPdf(vendedorId, month, year);
        String filename = "nomina_" + vendedorId + "_" + month + "_" + year + ".pdf";
        return buildResponse(bytes, MediaType.APPLICATION_PDF, filename);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    /**
     * Si el usuario es VENDEDOR, verifica que solo acceda a su propio ID.
     * OWNER y ADMIN pueden acceder a cualquier vendedor sin restricción.
     */
    private void validateVendedorAccess(UUID vendedorId, Authentication authentication) {
        boolean isVendedor = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VENDEDOR"));
        if (isVendedor) {
            User currentUser = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));
            if (!currentUser.getId().equals(vendedorId)) {
                throw new BusinessExeption("Solo puedes descargar tu propia nómina");
            }
        }
    }

    private ResponseEntity<byte[]> buildResponse(byte[] bytes, MediaType mediaType, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
