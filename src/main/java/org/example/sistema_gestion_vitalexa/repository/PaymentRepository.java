package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.Payment;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
}
