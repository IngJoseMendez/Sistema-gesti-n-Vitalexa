package org.example.sistema_gestion_vitalexa.controller.owner;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.CreateSaleGoalRequest;
import org.example.sistema_gestion_vitalexa.dto.SaleGoalResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSaleGoalRequest;
import org.example.sistema_gestion_vitalexa.dto.VendedorWithGoalResponse;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.SaleGoalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/sale-goals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class SaleGoalOwnerController {

    private final SaleGoalService saleGoalService;
    private final UserRepository userRepository;

    // ...existing code...

    /**
     * Recalcular los progresos de TODAS las metas de un mes/año.
     * Útil para corregir discrepancias entre el panel visual y el Excel.
     * POST /api/owner/sale-goals/recalculate?month=2&year=2026
     */
    @PostMapping("/recalculate")
    public ResponseEntity<String> recalculateAll(
            @RequestParam int month,
            @RequestParam int year) {

        List<User> vendedores = userRepository.findByRole(Role.VENDEDOR);
        int count = 0;
        for (User v : vendedores) {
            saleGoalService.recalculateGoalForVendorMonth(v.getId(), month, year);
            count++;
        }
        return ResponseEntity.ok("Recálculo completado para " + count + " vendedores en " + month + "/" + year);
    }

    /**
     * Ver todos los vendedores con su meta del mes actual
     */
    @GetMapping("/vendedores")
    public ResponseEntity<List<VendedorWithGoalResponse>> findAllVendedoresWithCurrentGoal() {
        List<VendedorWithGoalResponse> vendedores = saleGoalService.findAllVendedoresWithCurrentGoal();
        return ResponseEntity.ok(vendedores);
    }

    /**
     * Crear nueva meta para un vendedor
     */
    @PostMapping
    public ResponseEntity<SaleGoalResponse> createGoal(@Valid @RequestBody CreateSaleGoalRequest request) {
        SaleGoalResponse response = saleGoalService.createGoal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Actualizar meta existente
     */
    @PutMapping("/{id}")
    public ResponseEntity<SaleGoalResponse> updateGoal(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSaleGoalRequest request) {
        SaleGoalResponse response = saleGoalService.updateGoal(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Eliminar meta
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable UUID id) {
        saleGoalService.deleteGoal(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ver todas las metas
     */
    @GetMapping
    public ResponseEntity<List<SaleGoalResponse>> findAll() {
        List<SaleGoalResponse> goals = saleGoalService.findAll();
        return ResponseEntity.ok(goals);
    }

    /**
     * Ver metas de un mes/año específico
     */
    @GetMapping("/month")
    public ResponseEntity<List<SaleGoalResponse>> findByMonthAndYear(
            @RequestParam int month,
            @RequestParam int year) {
        List<SaleGoalResponse> goals = saleGoalService.findByMonthAndYear(month, year);
        return ResponseEntity.ok(goals);
    }

    /**
     * Ver meta específica
     */
    @GetMapping("/{id}")
    public ResponseEntity<SaleGoalResponse> findById(@PathVariable UUID id) {
        SaleGoalResponse goal = saleGoalService.findById(id);
        return ResponseEntity.ok(goal);
    }
}
