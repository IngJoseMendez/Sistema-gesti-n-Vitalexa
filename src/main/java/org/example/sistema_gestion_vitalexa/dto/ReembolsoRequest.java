package org.example.sistema_gestion_vitalexa.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ReembolsoRequest {
    private List<ReembolsoItemRequest> items;
    private String notas;

    @Data
    public static class ReembolsoItemRequest {
        private UUID productoId;
        private Integer cantidad;
    }
}
