package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sale_goals", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"vendedor_id", "month", "year"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private User vendedor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default  // ✅ AGREGAR ESTO
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @CreationTimestamp  // ✅ CAMBIAR A ESTO
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp  // ✅ CAMBIAR A ESTO
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ✅ ELIMINAR ESTOS MÉTODOS (ya no son necesarios con las anotaciones de Hibernate)
    // @PreUpdate
    // public void preUpdate() {
    //     this.updatedAt = LocalDateTime.now();
    // }

    /**
     * Calcula el porcentaje de cumplimiento de la meta
     */
    public BigDecimal getPercentage() {
        if (targetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(targetAmount, 2, RoundingMode.HALF_UP);
    }

    /**
     * Incrementa las ventas actuales
     */
    public void addSale(BigDecimal amount) {
        this.currentAmount = this.currentAmount.add(amount);
    }

    /**
     * Verifica si la meta está cumplida
     */
    public boolean isCompleted() {
        return currentAmount.compareTo(targetAmount) >= 0;
    }
}
