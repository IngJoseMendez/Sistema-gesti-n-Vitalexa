package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.PaymentTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentTransferRepository extends JpaRepository<PaymentTransfer, UUID> {

    /**
     * Suma de transferencias ACTIVAS sobre un pago específico.
     * Usado para calcular el saldo disponible: pago.amount - esta suma.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM PaymentTransfer t
            WHERE t.payment.id = :paymentId
              AND t.isRevoked = false
            """)
    BigDecimal sumActiveTransfersByPaymentId(@Param("paymentId") UUID paymentId);

    /**
     * Suma de transferencias ACTIVAS destinadas a un vendedor en un mes/año.
     * Usado en calculateTotalSold() para sumar al vendedor destino.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM PaymentTransfer t
            WHERE t.destVendedor.id = :vendedorId
              AND t.targetMonth = :month
              AND t.targetYear  = :year
              AND t.isRevoked   = false
            """)
    BigDecimal sumActiveTransfersToVendedorInMonth(
            @Param("vendedorId") UUID vendedorId,
            @Param("month") int month,
            @Param("year") int year);

    /**
     * Igual que el anterior pero para usuarios compartidos (ej: Nina/Yicela).
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM PaymentTransfer t
            WHERE t.destVendedor.id IN :vendedorIds
              AND t.targetMonth = :month
              AND t.targetYear  = :year
              AND t.isRevoked   = false
            """)
    BigDecimal sumActiveTransfersToVendedorIdsInMonth(
            @Param("vendedorIds") List<UUID> vendedorIds,
            @Param("month") int month,
            @Param("year") int year);

    /** Todas las transferencias de un pago (más recientes primero) */
    List<PaymentTransfer> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    /** Transferencias donde este vendedor es el ORIGEN */
    List<PaymentTransfer> findByOriginVendedorIdOrderByCreatedAtDesc(UUID originVendedorId);

    /** Transferencias donde este vendedor es el DESTINO */
    List<PaymentTransfer> findByDestVendedorIdOrderByCreatedAtDesc(UUID destVendedorId);
}
