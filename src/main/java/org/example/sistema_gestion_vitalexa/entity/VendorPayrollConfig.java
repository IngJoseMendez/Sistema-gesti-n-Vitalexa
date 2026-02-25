package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Configuración de nómina personalizada por vendedor.
 * El Owner puede ajustar los porcentajes y habilitar comisiones especiales.
 */
@Entity
@Table(name = "vendor_payroll_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorPayrollConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false, unique = true)
    private User vendedor;

    /** Salario base mensual del vendedor (0 si solo comisiones) */
    @Column(name = "base_salary", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal baseSalary = BigDecimal.ZERO;

    /** % comisión por meta de ventas (ej: 0.0150 = 1.5%) */
    @Column(name = "sales_commission_pct", precision = 5, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal salesCommissionPct = new BigDecimal("0.0150");

    /** % comisión por recaudo (ej: 0.0300 = 3%) */
    @Column(name = "collection_commission_pct", precision = 5, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal collectionCommissionPct = new BigDecimal("0.0300");

    /** % mínimo de recaudo para ganar comisión (ej: 0.80 = 80%) */
    @Column(name = "collection_threshold_pct", precision = 5, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal collectionThresholdPct = new BigDecimal("0.8000");

    /** ¿Está habilitado para recibir comisión general por metas globales? */
    @Column(name = "general_commission_enabled", nullable = false)
    @Builder.Default
    private Boolean generalCommissionEnabled = false;

    /** % comisión general (ej: 0.0200 = 2%) */
    @Column(name = "general_commission_pct", precision = 5, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal generalCommissionPct = new BigDecimal("0.0200");

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

