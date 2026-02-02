package org.example.sistema_gestion_vitalexa.enums;

/**
 * Tipos de facturas históricas para cuadre de caja
 */
public enum InvoiceType {
    NORMAL("Normal", "[Standard]"),
    SR("Remisión (S/R)", "[S/R]"),
    PROMO("Promoción", "[Promoción]");

    private final String label;
    private final String suffix;

    InvoiceType(String label, String suffix) {
        this.label = label;
        this.suffix = suffix;
    }

    public String getLabel() {
        return label;
    }

    public String getSuffix() {
        return suffix;
    }
}

