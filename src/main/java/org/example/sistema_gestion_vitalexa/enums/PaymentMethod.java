package org.example.sistema_gestion_vitalexa.enums;

/**
 * Métodos de pago disponibles para registrar abonos
 */
public enum PaymentMethod {
    EFECTIVO("Efectivo"),
    TRANSFERENCIA("Transferencia Bancaria"),
    CHEQUE("Cheque"),
    TARJETA("Tarjeta de Crédito/Débito"),
    CREDITO("Crédito"),
    OTRO("Otro");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

