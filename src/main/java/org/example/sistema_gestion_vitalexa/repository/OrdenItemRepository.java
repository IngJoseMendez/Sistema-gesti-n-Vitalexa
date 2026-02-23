package org.example.sistema_gestion_vitalexa.repository;

import org.example.sistema_gestion_vitalexa.dto.StockSummaryDTO;
import org.example.sistema_gestion_vitalexa.entity.OrderItem;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrdenItemRepository extends JpaRepository<OrderItem, UUID> {

    /**
     * Reporte de inventario con contexto de pedidos activos.
     *
     * LÓGICA: El stock se descuenta al CREAR el pedido en este sistema.
     * Por eso:
     *   - stockEnBD          = stock actual en base de datos (puede ser negativo)
     *   - stockComprometido  = unidades en pedidos activos (PENDIENTE/CONFIRMADO, no despachados)
     *   - stockFisicoReal    = stockEnBD + stockComprometido  (lo que hay en bodega físicamente)
     *
     * Se excluyen items de flete y bonificados del conteo de comprometido.
     * Se excluyen pedidos COMPLETADO, CANCELADO y ANULADA.
     */
    @Query("""
            SELECT new org.example.sistema_gestion_vitalexa.dto.StockSummaryDTO(
                p.id,
                p.nombre,
                p.stock,
                CAST(COALESCE(SUM(
                    CASE
                        WHEN oi.id IS NOT NULL
                             AND o.estado NOT IN (:estadosFinales)
                             AND (oi.isFreightItem = false OR oi.isFreightItem IS NULL)
                             AND (oi.isBonified = false OR oi.isBonified IS NULL)
                             AND (oi.isFreeItem = false OR oi.isFreeItem IS NULL)
                        THEN oi.cantidad
                        ELSE 0
                    END
                ), 0) AS integer),
                CAST(p.stock + COALESCE(SUM(
                    CASE
                        WHEN oi.id IS NOT NULL
                             AND o.estado NOT IN (:estadosFinales)
                             AND (oi.isFreightItem = false OR oi.isFreightItem IS NULL)
                             AND (oi.isBonified = false OR oi.isBonified IS NULL)
                             AND (oi.isFreeItem = false OR oi.isFreeItem IS NULL)
                        THEN oi.cantidad
                        ELSE 0
                    END
                ), 0) AS integer)
            )
            FROM Product p
            LEFT JOIN OrderItem oi ON oi.product.id = p.id
            LEFT JOIN oi.order o
            WHERE (p.isHidden = false OR p.isHidden IS NULL)
            GROUP BY p.id, p.nombre, p.stock
            ORDER BY p.nombre ASC
            """)
    List<StockSummaryDTO> findStockSummaryWithCommitted(
            @Param("estadosFinales") List<OrdenStatus> estadosFinales);

    /**
     * Calcula el stock comprometido (en pedidos activos) para UN producto específico.
     */
    @Query("""
            SELECT COALESCE(SUM(oi.cantidad), 0)
            FROM OrderItem oi
            JOIN oi.order o
            WHERE oi.product.id = :productId
              AND o.estado NOT IN (:estadosFinales)
              AND (oi.isFreightItem = false OR oi.isFreightItem IS NULL)
              AND (oi.isBonified = false OR oi.isBonified IS NULL)
              AND (oi.isFreeItem = false OR oi.isFreeItem IS NULL)
            """)
    Integer findStockComprometidoByProductId(
            @Param("productId") UUID productId,
            @Param("estadosFinales") List<OrdenStatus> estadosFinales);
}

