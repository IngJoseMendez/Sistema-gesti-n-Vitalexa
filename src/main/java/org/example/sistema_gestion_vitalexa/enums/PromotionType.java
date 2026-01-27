package org.example.sistema_gestion_vitalexa.enums;

/**
 * Tipos de promociones soportadas:
 * - PACK: Paquete con precio fijo (ej: 40+10 por $400.000)
 * - BUY_GET_FREE: Compra X y recibe Y gratis (ej: 13+1 gratis)
 */
public enum PromotionType {
    PACK, // Mapped to logic: "FIXED_PROMOTION" (Concreta)
    BUY_GET_FREE // Mapped to logic: "ASSORTMENT_PROMOTION" (Surtido)
}
