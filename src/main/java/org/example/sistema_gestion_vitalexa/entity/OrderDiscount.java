package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sistema_gestion_vitalexa.enums.DiscountStatus;
import org.example.sistema_gestion_vitalexa.enums.DiscountType;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad para auditoría de descuentos aplicados a órdenes.
 * Admin aplica descuentos, Owner puede revocarlos.
 */
@Entity
@Table(name = "order_discounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DiscountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DiscountStatus status = DiscountStatus.APPLIED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by")
    private User appliedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private User revokedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /**
     * Revoca el descuento
     */
    public void revoke(User owner) {
        this.status = DiscountStatus.REVOKED;
        this.revokedBy = owner;
        this.revokedAt = LocalDateTime.now();
    }
}
