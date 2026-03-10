package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.Payment;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrder(Order order);

    List<Payment> findByOrderId(UUID orderId);

    List<Payment> findByRegisteredBy(User user);

    // For shared users (Nina/Yicela) - find payments registered by multiple
    // usernames
    List<Payment> findByRegisteredByUsernameIn(List<String> usernames);

    void deleteByOrder(org.example.sistema_gestion_vitalexa.entity.Order order);

    /**
     * Suma total de pagos ACTIVOS para una orden (excluye anulados)
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.order.id = :orderId AND (p.isCancelled = false OR p.isCancelled IS NULL)")
    BigDecimal sumPaymentsByOrderId(@Param("orderId") UUID orderId);

    /**
     * Pagos ordenados por fecha (más recientes primero) - TODOS incluyendo anulados
     */
    List<Payment> findByOrderIdOrderByPaymentDateDesc(UUID orderId);

    /**
     * Pagos ACTIVOS ordenados por fecha real (más recientes primero)
     */
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId AND (p.isCancelled = false OR p.isCancelled IS NULL) ORDER BY p.actualPaymentDate DESC")
    List<Payment> findActiveByOrderIdOrderByPaymentDateDesc(@Param("orderId") UUID orderId);

    /**
     * Pagos ACTIVOS de un cliente específico
     */
    @Query("SELECT p FROM Payment p WHERE p.order.cliente.id = :clientId AND (p.isCancelled = false OR p.isCancelled IS NULL) ORDER BY p.actualPaymentDate DESC")
    List<Payment> findByClientIdAndNotCancelled(@Param("clientId") UUID clientId);

    /**
     * Pagos ACTIVOS ordenados por fecha actual del pago
     */
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId AND (p.isCancelled = false OR p.isCancelled IS NULL) ORDER BY p.actualPaymentDate DESC, p.paymentDate DESC")
    List<Payment> findActivePaymentsByOrderId(@Param("orderId") UUID orderId);

    /**
     * Suma TODOS los pagos activos de órdenes cuya fecha (o.fecha) cae en el mes
     * anterior (orderStart..orderEnd), SIEMPRE QUE el pago real (actualPaymentDate)
     * haya ocurrido antes del fin del mes de nómina (payEndDate).
     *
     * Regla:
     *   - Factura de enero pagada en enero   → recaudo de febrero ✔
     *   - Factura de enero pagada en febrero  → recaudo de febrero ✔
     *   - Factura de enero pagada en marzo    → NO entra en ningún recaudo ❌
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.order.vendedor.id = :vendedorId
            AND (p.isCancelled = false OR p.isCancelled IS NULL)
            AND p.order.fecha >= :orderStart
            AND p.order.fecha < :orderEnd
            AND (p.actualPaymentDate IS NULL OR p.actualPaymentDate < :payEndDate)
            """)
    BigDecimal sumCollectedByVendedorBetween(
            @Param("vendedorId") UUID vendedorId,
            @Param("orderStart") LocalDateTime orderStart,
            @Param("orderEnd") LocalDateTime orderEnd,
            @Param("payEndDate") java.time.LocalDate payEndDate);

    /**
     * Igual que el anterior pero para usuarios compartidos (NinaTorres/YicelaSandoval).
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.order.vendedor.id IN :vendedorIds
            AND (p.isCancelled = false OR p.isCancelled IS NULL)
            AND p.order.fecha >= :orderStart
            AND p.order.fecha < :orderEnd
            AND (p.actualPaymentDate IS NULL OR p.actualPaymentDate < :payEndDate)
            """)
    BigDecimal sumCollectedByVendedorIdsBetween(
            @Param("vendedorIds") List<UUID> vendedorIds,
            @Param("orderStart") LocalDateTime orderStart,
            @Param("orderEnd") LocalDateTime orderEnd,
            @Param("payEndDate") java.time.LocalDate payEndDate);
}
