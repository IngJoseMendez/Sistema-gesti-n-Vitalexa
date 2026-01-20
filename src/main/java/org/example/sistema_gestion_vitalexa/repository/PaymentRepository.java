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

    /**
     * Suma total de pagos para una orden
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.order.id = :orderId")
    BigDecimal sumPaymentsByOrderId(@Param("orderId") UUID orderId);

    /**
     * Pagos ordenados por fecha (más recientes primero)
     */
    List<Payment> findByOrderIdOrderByPaymentDateDesc(UUID orderId);
}
