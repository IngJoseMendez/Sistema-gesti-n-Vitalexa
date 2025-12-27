package org.example.sistema_gestion_vitalexa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "reembolso_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReembolsoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "reembolso_id", nullable = false)
    @JsonIgnore
    private Reembolso reembolso;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Product producto;

    @Column(nullable = false)
    private Integer cantidad;
}
