package org.example.sistema_gestion_vitalexa.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReembolsoResponse {
    private UUID id;
    private String empacadorUsername;
    private List<ReembolsoItemResponse> items;
    private LocalDateTime fecha;
    private String notas;
    private String estado;

    @Data
    @Builder
    public static class ReembolsoItemResponse {
        private UUID productoId;
        private String productoNombre;
        private String productoImageUrl;
        private Integer cantidad;
    }
}
