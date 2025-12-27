package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reembolsos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reembolso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "empacador_id", nullable = false)
    private User empacador;

    @OneToMany(mappedBy = "reembolso", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReembolsoItem> items = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(length = 500)
    private String notas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReembolso estado;

    @PrePersist
    protected void onCreate() {
        fecha = LocalDateTime.now();
        if (estado == null) {
            estado = EstadoReembolso.CONFIRMADO;
        }
    }

    public enum EstadoReembolso {
        CONFIRMADO, CANCELADO
    }
}
