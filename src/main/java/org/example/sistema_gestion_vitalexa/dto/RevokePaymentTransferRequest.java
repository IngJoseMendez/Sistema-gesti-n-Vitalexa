package org.example.sistema_gestion_vitalexa.dto;

/**
 * Request para revocar una transferencia de pago.
 *
 * @param reason Motivo de la revocación (requerido)
 */
public record RevokePaymentTransferRequest(String reason) {
}
