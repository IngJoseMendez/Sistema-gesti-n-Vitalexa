package org.example.sistema_gestion_vitalexa.entity.enums;

public enum InventoryMovementType {
    CREATION, // Creación de producto
    UPDATE, // Actualización manual (precio/nombre/etc que no afecta stock, o ajuste stock
            // manual)
    STOCK_ADJUSTMENT, // Cambio específico de stock manual
    SALE, // Venta (Orden)
    RESTOCK, // Reabastecimiento
    DELETION, // Eliminación de producto
    RETURN // Devolución/Reembolso
}
