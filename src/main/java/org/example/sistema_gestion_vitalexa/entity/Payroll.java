package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad que representa el cálculo de nómina mensual de un vendedor.
 * Se calcula al finalizar cada mes y contempla:
 * - Salario base (opcional)
 * - Comisión por meta de ventas (1.5% si cumple la meta)
 * - Comisión por meta de recaudo (3% si recauda ≥80% del mes anterior)
 * - Comisión general por metas globales (2% de la suma de todas las metas)
 * → Solo aplica si las ventas totales de la empresa >= suma de metas de todos
 * los vendedores
 */
@Entity
@Table(name = "payrolls", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "vendedor_id", "month", "year" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private User vendedor;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    // ─── Salario Base ───────────────────────────────────────────
    /** Salario base fijo mensual (puede ser 0 si solo trabaja por comisiones) */
    @Column(name = "base_salary", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal baseSalary = BigDecimal.ZERO;

    // ─── Comisión por Meta de Ventas ────────────────────────────
    /** Meta de ventas definida para el mes */
    @Column(name = "sales_goal_target", precision = 12, scale = 2)
    private BigDecimal salesGoalTarget;

    /** Total vendido en el mes */
    @Column(name = "total_sold", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalSold = BigDecimal.ZERO;

    /** ¿Cumplió la meta de ventas? */
    @Column(name = "sales_goal_met", nullable = false)
    @Builder.Default
    private Boolean salesGoalMet = false;

    /** Porcentaje de comisión por ventas (configurable, por defecto 1.5%) */
    @Column(name = "sales_commission_pct", precision = 5, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal salesCommissionPct = new BigDecimal("0.0150");

    /**
     * true  → comisión de ventas aplicó solo por haber cumplido la meta.
     * false → comisión de ventas se aplicó directamente sobre lo vendido, sin meta.
     */
    @Column(name = "sales_commission_by_goal", nullable = false)
    @Builder.Default
    private Boolean salesCommissionByGoal = true;

    /** Valor de la comisión por meta de ventas (0 si no cumplió cuando byGoal=true) */
    @Column(name = "sales_commission_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal salesCommissionAmount = BigDecimal.ZERO;

    // ─── Comisión por Meta de Recaudo ───────────────────────────
    /** Total vendido el MES ANTERIOR (base del recaudo) */
    @Column(name = "prev_month_total_sold", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal prevMonthTotalSold = BigDecimal.ZERO;

    /** Total efectivamente recaudado del mes anterior */
    @Column(name = "total_collected", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalCollected = BigDecimal.ZERO;

    /** Porcentaje de recaudo logrado (0.00 a 999.9999 — ej: 83.3333 = 83.33%) */
    @Column(name = "collection_pct", precision = 7, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal collectionPct = BigDecimal.ZERO;

    /** ¿Cumplió el 80% de recaudo? */
    @Column(name = "collection_goal_met", nullable = false)
    @Builder.Default
    private Boolean collectionGoalMet = false;

    /** Porcentaje de comisión por recaudo (configurable, por defecto 3%) */
    @Column(name = "collection_commission_pct", precision = 5, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal collectionCommissionPct = new BigDecimal("0.0300");

    /**
     * true  → comisión de recaudo aplicó solo por superar el umbral.
     * false → comisión de recaudo se aplicó directamente sobre lo recaudado, sin umbral.
     */
    @Column(name = "collection_commission_by_goal", nullable = false)
    @Builder.Default
    private Boolean collectionCommissionByGoal = true;

    /** Valor de la comisión por recaudo (0 si no cumplió el umbral cuando byGoal=true) */
    @Column(name = "collection_commission_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal collectionCommissionAmount = BigDecimal.ZERO;

    // ─── Comisión General por Metas Globales ────────────────────
    /** ¿Está habilitado para recibir comisión general? */
    @Column(name = "general_commission_enabled", nullable = false)
    @Builder.Default
    private Boolean generalCommissionEnabled = false;

    /** Suma total de todas las metas globales del mes */
    @Column(name = "total_global_goals", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalGlobalGoals = BigDecimal.ZERO;

    /**
     * Total de ventas de TODA la empresa en el mes (suma de todos los vendedores).
     * Debe ser >= effectiveThreshold para que la comisión general aplique.
     */
    @Column(name = "total_company_sales", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalCompanySales = BigDecimal.ZERO;

    /**
     * Umbral efectivo usado para la comisión general.
     * Puede ser la suma de metas (thresholdIsCustom=false) o un valor personalizado
     * del Owner.
     */
    @Column(name = "effective_threshold", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal effectiveThreshold = BigDecimal.ZERO;

    /**
     * true si el Owner proporcionó un umbral manual; false si se usó la suma de
     * metas.
     */
    @Column(name = "threshold_is_custom", nullable = false)
    @Builder.Default
    private Boolean thresholdIsCustom = false;

    /**
     * ¿Se cumplió el umbral de ventas globales (totalCompanySales >=
     * effectiveThreshold)?
     */
    @Column(name = "general_commission_goal_met", nullable = false)
    @Builder.Default
    private Boolean generalCommissionGoalMet = false;

    /** Porcentaje de comisión general (configurable, por defecto 2%) */
    @Column(name = "general_commission_pct", precision = 5, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal generalCommissionPct = new BigDecimal("0.0200");

    /** Valor de la comisión general */
    @Column(name = "general_commission_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal generalCommissionAmount = BigDecimal.ZERO;

    // ─── Totales ────────────────────────────────────────────────
    /** Total de comisiones (ventas + recaudo + general) */
    @Column(name = "total_commissions", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalCommissions = BigDecimal.ZERO;

    /** Total a pagar (base + comisiones) */
    @Column(name = "total_payout", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalPayout = BigDecimal.ZERO;

    // ─── Auditoría ──────────────────────────────────────────────
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "calculated_by")
    private UUID calculatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Recalcular totales a partir de los componentes individuales.
     */
    public void recalculateTotals() {
        this.totalCommissions = salesCommissionAmount
                .add(collectionCommissionAmount)
                .add(generalCommissionAmount);
        this.totalPayout = baseSalary.add(totalCommissions);
    }
}
