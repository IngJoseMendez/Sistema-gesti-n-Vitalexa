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

        @Query(value = "SELECT COALESCE(MAX(invoice_number), 0) FROM orders", nativeQuery = true)
        Long findMaxInvoiceNumber();

        @Query(value = "SELECT setval('invoice_number_seq', ?1, false)", nativeQuery = true)
        Long syncInvoiceSequence(Long nextValue);

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
         * Buscar órdenes completadas de MÚLTIPLES vendedores en un mes/año específico
         * Útil para usuarios compartidos como NinaTorres/YicelaSandoval
         */
        @Query("""
                        SELECT o FROM Order o
                        WHERE o.vendedor.id IN :vendedorIds
                        AND o.estado = 'COMPLETADO'
                        AND MONTH(o.fecha) = :month
                        AND YEAR(o.fecha) = :year
                        """)
        List<Order> findCompletedOrdersByVendedorIdsAndMonthYear(
                        @Param("vendedorIds") List<UUID> vendedorIds,
                        @Param("month") int month,
                        @Param("year") int year);

        /**
         * Para reportes: órdenes en un rango de fechas (por fecha de creación)
         */
        @Query("SELECT o FROM Order o WHERE o.fecha BETWEEN :start AND :end")
        List<Order> findByFechaBetween(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        /**
         * Para reportes: órdenes COMPLETADAS filtradas por fecha de completado (completedAt).
         * Si completedAt es null (órdenes históricas), usa fecha como fallback.
         */
        @Query("""
                        SELECT o FROM Order o
                        WHERE o.estado = 'COMPLETADO'
                        AND (
                            (o.completedAt IS NOT NULL AND o.completedAt BETWEEN :start AND :end)
                            OR
                            (o.completedAt IS NULL AND o.fecha BETWEEN :start AND :end)
                        )
                        """)
        List<Order> findCompletedByCompletedAtBetween(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("""
                        SELECT o FROM Order o
                        WHERE o.vendedor.id = :vendedorId
                        AND o.estado = 'COMPLETADO'
                        AND MONTH(o.fecha) = :month
                        AND YEAR(o.fecha) = :year
                        ORDER BY o.fecha DESC
                        """)
        List<Order> findByVendedorIdAndMonth(@Param("vendedorId") UUID vendedorId,
                        @Param("month") int month,
                        @Param("year") int year);

        /**
         * Buscar órdenes por estado y rango de fechas
         */
        @Query("""
                        SELECT o FROM Order o
                        WHERE o.estado = :estado
                        AND o.fecha >= :startDate
                        AND o.fecha < :endDate
                        ORDER BY o.fecha DESC
                        """)
        List<Order> findByEstadoAndFechaBetween(
                        @Param("estado") OrdenStatus estado,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * Buscar órdenes completadas entre un rango de fechas
         * Para reportes de productos y clientes en períodos específicos
         */
        default List<Order> findCompletedOrdersBetween(LocalDateTime startDate, LocalDateTime endDate) {
                return findByEstadoAndFechaBetween(OrdenStatus.COMPLETADO, startDate, endDate);
        }

        /**
         * Obtener ingresos totales en un rango de fechas
         */
        @Query("""
                        SELECT COALESCE(SUM(o.total), 0)
                        FROM Order o
                        WHERE o.estado = 'COMPLETADO'
                        AND o.fecha >= :startDate
                        AND o.fecha < :endDate
                        """)
        BigDecimal getTotalRevenueBetween(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * Buscar orden por ID con EAGER loading de items, productos y promociones
         * Útil para generar facturas PDF que necesitan acceso a las promociones
         */
        @Query("""
                        SELECT DISTINCT o FROM Order o
                        LEFT JOIN FETCH o.items items
                        LEFT JOIN FETCH items.product
                        LEFT JOIN FETCH items.promotion
                        LEFT JOIN FETCH o.vendedor
                        LEFT JOIN FETCH o.cliente
                        WHERE o.id = :orderId
                        """)
        Optional<Order> findByIdWithPromotions(@Param("orderId") UUID orderId);

        /**
         * Para nómina: suma total de órdenes NO ANULADAS de un vendedor
         * en un rango de fechas calendario exacto (inicio y fin del mes).
         * Usa o.fecha (fecha de creación/factura) para respetar el mes calendario.
         */
        @Query("""
                        SELECT COALESCE(SUM(o.total), 0)
                        FROM Order o
                        WHERE o.vendedor.id = :vendedorId
                        AND o.estado <> 'ANULADA'
                        AND o.fecha >= :start
                        AND o.fecha < :end
                        """)
        BigDecimal sumTotalSoldByVendedorBetween(
                        @Param("vendedorId") UUID vendedorId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        /**
         * Para nómina con usuarios compartidos (NinaTorres/YicelaSandoval).
         */
        @Query("""
                        SELECT COALESCE(SUM(o.total), 0)
                        FROM Order o
                        WHERE o.vendedor.id IN :vendedorIds
                        AND o.estado <> 'ANULADA'
                        AND o.fecha >= :start
                        AND o.fecha < :end
                        """)
        BigDecimal sumTotalSoldByVendedorIdsBetween(
                        @Param("vendedorIds") List<UUID> vendedorIds,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);
}
