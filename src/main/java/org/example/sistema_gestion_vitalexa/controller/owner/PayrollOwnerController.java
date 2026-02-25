package org.example.sistema_gestion_vitalexa.controller.owner;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.PayrollService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Controller para gestión de nóminas.
 * - Configurar comisiones (POST /config): solo OWNER
 * - Calcular todos (POST /calculate-all): solo OWNER
 * - Recalcular uno (POST /calculate): OWNER y ADMIN
 * - Consultas (GET): OWNER y ADMIN
 */
@RestController
@RequestMapping("/api/owner/payroll")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class PayrollOwnerController {

    private final PayrollService payrollService;
    private final UserRepository userRepository;

    // ─── Configuración ─────────────────────────────────────────────────────

    /**
     * GET /api/owner/payroll/config
     * Listar configuraciones de nómina de todos los vendedores
     */
    @GetMapping("/config")
    public ResponseEntity<List<VendorPayrollConfigResponse>> getAllConfigs() {
        return ResponseEntity.ok(payrollService.getAllConfigs());
    }

    /**
     * GET /api/owner/payroll/config/{vendedorId}
     */
    @GetMapping("/config/{vendedorId}")
    public ResponseEntity<VendorPayrollConfigResponse> getConfig(@PathVariable UUID vendedorId) {
        return ResponseEntity.ok(payrollService.getConfig(vendedorId));
    }

    /**
     * POST /api/owner/payroll/config — SOLO OWNER
     * Crear o actualizar la configuración de nómina de un vendedor
     */
    @PostMapping("/config")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<VendorPayrollConfigResponse> saveConfig(
            @Valid @RequestBody VendorPayrollConfigRequest request) {
        return ResponseEntity.ok(payrollService.saveConfig(request));
    }

    // ─── Cálculo ───────────────────────────────────────────────────────────

    /**
     * POST /api/owner/payroll/calculate
     * Calcular (o recalcular) la nómina de un vendedor. OWNER y ADMIN.
     */
    @PostMapping("/calculate")
    public ResponseEntity<PayrollResponse> calculatePayroll(
            @Valid @RequestBody CalculatePayrollRequest request,
            Authentication authentication) {

        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        PayrollResponse response = payrollService.calculatePayroll(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/owner/payroll/calculate-all?month=1&year=2026 — OWNER y ADMIN
     * Calcular nóminas de TODOS los vendedores activos para un mes/año.
     *
     * Parámetro opcional: generalCommissionThreshold
     * - Si se envía, se usará ese valor como umbral de ventas de la empresa.
     * - Si no se envía (null), se usa la suma de todas las metas de los vendedores.
     */
    @PostMapping("/calculate-all")
    public ResponseEntity<List<PayrollResponse>> calculateAllPayrolls(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) BigDecimal generalCommissionThreshold,
            Authentication authentication) {

        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        List<PayrollResponse> responses = payrollService.calculateAllPayrolls(
                month, year, currentUser.getId(), generalCommissionThreshold);
        return ResponseEntity.ok(responses);
    }

    // ─── Consultas ─────────────────────────────────────────────────────────

    /**
     * GET /api/owner/payroll?month=1&year=2026
     */
    @GetMapping
    public ResponseEntity<List<PayrollResponse>> getPayrollsByMonthYear(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(payrollService.findByMonthAndYear(month, year));
    }

    /**
     * GET /api/owner/payroll/{vendedorId}?month=1&year=2026
     */
    @GetMapping("/{vendedorId}")
    public ResponseEntity<PayrollResponse> getPayrollByVendedor(
            @PathVariable UUID vendedorId,
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(payrollService.findByVendedorAndMonthYear(vendedorId, month, year));
    }

    /**
     * GET /api/owner/payroll/{vendedorId}/history
     */
    @GetMapping("/{vendedorId}/history")
    public ResponseEntity<List<PayrollResponse>> getPayrollHistory(@PathVariable UUID vendedorId) {
        return ResponseEntity.ok(payrollService.findHistoryByVendedor(vendedorId));
    }
}
