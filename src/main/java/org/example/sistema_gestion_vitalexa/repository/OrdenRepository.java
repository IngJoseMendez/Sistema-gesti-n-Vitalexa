package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.entity.Client;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrdenRepository extends JpaRepository<Order, UUID> {
        List<Order> findByVendedor(User vendedor);

        // For shared users (Nina/Yicela) - find orders by multiple usernames
        List<Order> findByVendedorUsernameIn(List<String> usernames);

        Optional<Order> findByIdAndVendedorUsername(UUID id, String username);

        List<Order> findByEstado(OrdenStatus estado);

        List<Order> findByCliente(Client client);

        Optional<Order> findByInvoiceNumber(Long invoiceNumber);

        @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
        Long nextInvoiceNumber();

        /**
         * Buscar órdenes completadas de un vendedor en un mes/año específico
         */
        @Query("""
                        SELECT o FROM Order o
                        WHERE o.vendedor.id = :vendedorId
                        AND o.estado = 'COMPLETADO'
                        AND MONTH(o.fecha) = :month
                        AND YEAR(o.fecha) = :year
                        """)
        List<Order> findCompletedOrdersByVendedorAndMonthYear(
                        @Param("vendedorId") UUID vendedorId,
                        @Param("month") int month,
                        @Param("year") int year);

        /**
         * Para reportes: órdenes en un rango de fechas
         */
        @Query("SELECT o FROM Order o WHERE o.fecha BETWEEN :start AND :end")
        List<Order> findByFechaBetween(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        /**
         * Buscar orden por ID con EAGER loading de items, productos y promociones
         * Útil para generar facturas PDF que necesitan acceso a las promociones
         */
        @Query("""
                        SELECT DISTINCT o FROM Order o
                        LEFT JOIN FETCH o.items items
                        LEFT JOIN FETCH items.product
                        LEFT JOIN FETCH items.promotion
                        WHERE o.id = :orderId
                        """)
        Optional<Order> findByIdWithPromotions(@Param("orderId") UUID orderId);
}
