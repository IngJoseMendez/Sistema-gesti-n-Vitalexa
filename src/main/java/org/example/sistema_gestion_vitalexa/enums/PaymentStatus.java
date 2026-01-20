package org.example.sistema_gestion_vitalexa.enums;

/**
 * Estado de pago de una orden
 */
public enum PaymentStatus {
    PENDING, // Sin pagos registrados
    PARTIAL, // Pago parcial (abonado pero no completo)
    PAID // Totalmente pagado
}
