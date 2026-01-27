package org.example.sistema_gestion_vitalexa.dto;

import java.time.LocalDate;

public record UpdateEtaRequest(
        LocalDate eta,
        String note) {
}
