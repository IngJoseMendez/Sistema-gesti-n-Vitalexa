package org.example.sistema_gestion_vitalexa.dto;

import java.util.UUID;

/**
 * DTO para el reporte de inventario con contexto de pedidos activos.
 *
 * IMPORTANTE: En este sistema el stock se descuenta al CREAR el pedido (no al despachar).
 *
 * stockEnBD          → Lo que dice la BD (ya descontó pedidos activos, puede ser negativo)
 * stockComprometido  → Unidades en pedidos ACTIVOS (PENDIENTE/CONFIRMADO, aún no despachados)
 * stockFisicoReal    → MAX(0, stockEnBD + stockComprometido) = lo que hay FÍSICAMENTE en bodega.
 *                      Nunca negativo: si el cálculo da negativo significa que hubo
 *                      ventas históricas que dejaron el stock en deuda, pero físicamente hay 0.
 */
public record StockSummaryDTO(
        UUID productId,
        String nombre,
        Integer stockEnBD,           // Stock en BD (puede ser negativo)
        Integer stockComprometido,   // Unidades en pedidos activos pendientes de despacho
        Integer stockFisicoRealRaw   // Valor crudo de la query (stockEnBD + comprometido)
) {
    /**
     * Stock físico real en bodega. Nunca negativo:
     * si el resultado es < 0, significa que hay deuda de inventario histórica,
     * pero físicamente no puede haber menos de 0 unidades.
     */
    public int stockFisicoReal() {
        int raw = stockFisicoRealRaw != null ? stockFisicoRealRaw : 0;
        return Math.max(0, raw);
    }

    /**
     * Indica si el stock en BD es negativo (más comprometido históricamente que existencias)
     */
    public boolean isAlertaCritica() {
        return stockEnBD != null && stockEnBD < 0;
    }

    /**
     * Indica si hay pedidos activos con este producto pendientes de despacho
     */
    public boolean tieneStockComprometido() {
        return stockComprometido != null && stockComprometido > 0;
    }
}

