package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.OrderDiscount;
import org.example.sistema_gestion_vitalexa.enums.DiscountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, UUID> {

    List<OrderDiscount> findByOrder(Order order);

    List<OrderDiscount> findByOrderId(UUID orderId);

    List<OrderDiscount> findByOrderIdAndStatus(UUID orderId, DiscountStatus status);

    /**
     * Suma de porcentajes de descuentos activos para una orden
     */
    @Query("SELECT COALESCE(SUM(d.percentage), 0) FROM OrderDiscount d WHERE d.order.id = :orderId AND d.status = 'APPLIED'")
    BigDecimal sumActiveDiscountsByOrderId(@Param("orderId") UUID orderId);

    /**
     * Descuentos ordenados por fecha de creación
     */
    List<OrderDiscount> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
