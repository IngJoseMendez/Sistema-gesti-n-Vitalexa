package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registra la asignación (transferencia) del valor de un pago ya realizado
 * a otro vendedor, sumando al totalSold del mes destino para efectos de nómina.
 *
 * Reglas:
 * - Solo pagos activos (no cancelados) pueden ser transferidos.
 * - El monto transferido nunca puede superar el saldo disponible del pago
 * (pago.amount - suma de transferencias activas sobre ese pago).
 * - La revocación es un soft-delete: isRevoked = true.
 * - El vendedor origen NO pierde su totalSold; el destino simplemente lo gana.
 */
@Entity
@Table(name = "payment_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Pago sobre el que se hace la transferencia */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /** Vendedor dueño de la orden (para auditoría, se guarda al crear) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_vendedor_id", nullable = false)
    private User originVendedor;

    /** Vendedor que recibe el crédito de ventas */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_vendedor_id", nullable = false)
    private User destVendedor;

    /** Monto transferido (≤ saldo disponible del pago) */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Mes al que se suma en las ventas del vendedor destino (1-12) */
    @Column(name = "target_month", nullable = false)
    private Integer targetMonth;

    /** Año al que se suma en las ventas del vendedor destino */
    @Column(name = "target_year", nullable = false)
    private Integer targetYear;

    /** Motivo de la transferencia (opcional) */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /** false = activa | true = revocada */
    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private Boolean isRevoked = false;

    // ─── Auditoría de revocación ─────────────────────────────────────────────

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private User revokedBy;

    @Column(name = "revocation_reason", columnDefinition = "TEXT")
    private String revocationReason;

    // ─── Auditoría de creación ───────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
