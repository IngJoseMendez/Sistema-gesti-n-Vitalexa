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

    private String nit;

    // Persona que administra el establecimiento
    private String administrador;

    // Representante legal del cliente
    private String representanteLegal;

    // NUEVO: User que se autentica como CLIENTE
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // NUEVO: Vendedor asignado a este cliente (no default global)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_asignado_id")
    private User vendedorAsignado;

    // Tope máximo de ventas (control de crédito)
    @Column(name = "credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    // Saldo inicial (deudas previas al sistema)
    @Column(name = "initial_balance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal initialBalance = BigDecimal.ZERO;

    // Flag para asegurar que el saldo inicial solo se establece una vez
    @Column(name = "initial_balance_set")
    @Builder.Default
    private Boolean initialBalanceSet = false;

    public void registerPurchase(BigDecimal monto) {
        if (this.totalCompras == null)
            this.totalCompras = BigDecimal.ZERO;
        this.totalCompras = this.totalCompras.add(monto);
        this.ultimaCompra = LocalDateTime.now();
    }
}
