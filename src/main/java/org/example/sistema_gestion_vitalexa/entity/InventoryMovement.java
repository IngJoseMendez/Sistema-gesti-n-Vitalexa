package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sistema_gestion_vitalexa.entity.enums.InventoryMovementType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // Guardamos nombre/sku del producto por si se elimina físicamente
    private String productName;

    @Enumerated(EnumType.STRING)
    private InventoryMovementType type;

    private Integer quantity; // Cantidad afectada (puede ser 0 en updates de info)

    private Integer previousStock;

    private Integer newStock;

    private String reason; // "Venta #123", "Ajuste manual", etc.

    private String username; // Quién hizo el movimiento

    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
