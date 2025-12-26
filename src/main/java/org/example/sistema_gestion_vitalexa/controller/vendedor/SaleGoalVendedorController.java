package org.example.sistema_gestion_vitalexa.controller.vendedor;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.SaleGoalResponse;
import org.example.sistema_gestion_vitalexa.service.SaleGoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vendedor/sale-goals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDEDOR')")
public class SaleGoalVendedorController {

    private final SaleGoalService saleGoalService;

    /**
     * Ver MI meta del mes actual
     */
    @GetMapping("/my")
    public ResponseEntity<SaleGoalResponse> findMyCurrentGoal(Authentication authentication) {
        String username = authentication.getName();
        SaleGoalResponse goal = saleGoalService.findMyCurrentGoal(username);
        return ResponseEntity.ok(goal);
    }

    /**
     * Ver historial de MIS metas
     */
    @GetMapping("/history")
    public ResponseEntity<List<SaleGoalResponse>> findMyGoalHistory(Authentication authentication) {
        String username = authentication.getName();
        List<SaleGoalResponse> history = saleGoalService.findMyGoalHistory(username);
        return ResponseEntity.ok(history);
    }
}
