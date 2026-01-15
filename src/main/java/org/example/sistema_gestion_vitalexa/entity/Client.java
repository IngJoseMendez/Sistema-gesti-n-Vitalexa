package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    private BigDecimal totalCompras = BigDecimal.ZERO;

    private String email;

    private LocalDateTime ultimaCompra;

    private String direccion;

    private String telefono;

    private boolean active = true;

    // NUEVO: User que se autentica como CLIENTE
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // NUEVO: Vendedor asignado a este cliente (no default global)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_asignado_id")
    private User vendedorAsignado;

    public void registerPurchase(BigDecimal monto) {
        if (this.totalCompras == null) this.totalCompras = BigDecimal.ZERO;
        this.totalCompras = this.totalCompras.add(monto);
        this.ultimaCompra = LocalDateTime.now();
    }
}

