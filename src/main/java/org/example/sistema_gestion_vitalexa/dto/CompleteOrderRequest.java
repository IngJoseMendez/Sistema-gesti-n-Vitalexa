package org.example.sistema_gestion_vitalexa.dto;

/**
 * Request para completar una orden con fecha de completado opcional.
 *
 * <p>
 * {@code completedAt} se acepta en cualquiera de los siguientes formatos:
 * <ul>
 * <li>{@code "2026-01-15"} — fecha ISO (yyyy-MM-dd)</li>
 * <li>{@code "2026-01-15T00:00:00"} — datetime ISO</li>
 * <li>{@code null} o ausente — usa la fecha y hora actuales del sistema</li>
 * </ul>
 *
 * <p>
 * Si el valor no puede parsearse, el sistema lanzará un error descriptivo.
 */
public record CompleteOrderRequest(

                /**
                 * Fecha de completado manual como string.
                 * Si es null o vacío, el sistema asigna la fecha y hora actuales.
                 * Formatos aceptados: "yyyy-MM-dd" o "yyyy-MM-dd'T'HH:mm:ss"
                 */
                String completedAt,

                /**
                 * Nota de auditoría opcional.
                 * Ejemplos: "Factura de diciembre registrada con retraso", "Liquidación enero"
                 */
                String auditNote) {
}
