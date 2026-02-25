package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.PayrollResponse;
import org.example.sistema_gestion_vitalexa.service.PayrollService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para que los vendedores consulten su propia nómina.
 * Solo pueden ver sus propios datos, el Owner calcula la nómina.
 */
@RestController
@RequestMapping("/api/vendedor/payroll")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
public class PayrollVendedorController {

    private final PayrollService payrollService;

    /**
     * GET /api/vendedor/payroll?month=1&year=2026
     * Ver mi nómina de un mes/año específico
     */
    @GetMapping
    public ResponseEntity<PayrollResponse> getMyPayroll(
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {
        PayrollResponse response = payrollService.findMyPayroll(authentication.getName(), month, year);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/vendedor/payroll/history
     * Ver mi historial completo de nóminas
     */
    @GetMapping("/history")
    public ResponseEntity<List<PayrollResponse>> getMyPayrollHistory(Authentication authentication) {
        List<PayrollResponse> history = payrollService.findMyPayrollHistory(authentication.getName());
        return ResponseEntity.ok(history);
    }
}

