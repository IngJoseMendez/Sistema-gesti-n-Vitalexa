package org.example.sistema_gestion_vitalexa.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.entity.InventoryMovement;
import org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType;
import org.example.sistema_gestion_vitalexa.service.InventoryMovementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/inventory/history")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class InventoryHistoryController {

    private final InventoryMovementService movementService;

    @GetMapping
    public ResponseEntity<Page<InventoryMovement>> getHistory(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) InventoryMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(movementService.getHistory(productId, type, startDate, endDate, pageable));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportHistory(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) InventoryMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            org.springframework.security.core.Authentication authentication) {

        String username = authentication != null ? authentication.getName() : "Unknown";
        List<InventoryMovement> movements = movementService.getAllHistory(productId, type, startDate, endDate);

        String filterDesc = "Filtros: " +
                (productId != null ? "Producto ID=" + productId + " " : "") +
                (type != null ? "Tipo=" + type + " " : "") +
                (startDate != null ? "Desde=" + startDate + " " : "") +
                (endDate != null ? "Hasta=" + endDate : "");

        byte[] pdfBytes = movementService.generateHistoryPdf(movements, username, filterDesc);

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=historial_inventario_" + System.currentTimeMillis() + ".pdf")
                .header("Content-Type", "application/pdf")
                .body(pdfBytes);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportSingleMovement(
            @PathVariable UUID id,
            org.springframework.security.core.Authentication authentication) {

        String username = authentication != null ? authentication.getName() : "Unknown";
        InventoryMovement movement = movementService.findById(id);

        byte[] pdfBytes = movementService.generateHistoryPdf(List.of(movement), username,
                "Reporte Individual Movimiento ID: " + id);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=movimiento_" + id + ".pdf")
                .header("Content-Type", "application/pdf")
                .body(pdfBytes);
    }
}
