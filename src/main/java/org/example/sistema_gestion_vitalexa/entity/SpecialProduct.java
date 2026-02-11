package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "special_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private BigDecimal precio;

    /**
     * Stock propio — solo se utiliza cuando parentProduct es null (standalone).
     * Cuando hay padre, el stock efectivo es parentProduct.stock.
     */
    @Column(name = "own_stock")
    private Integer ownStock;

    private String imageUrl;

    @Builder.Default
    private boolean active = true;

    @Column(name = "reorder_point")
    private Integer reorderPoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_tag_id")
    private ProductTag tag;

    /**
     * Producto padre (hard link). Null = standalone.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_product_id")
    private Product parentProduct;

    /**
     * Vendedores que pueden ver y vender este producto especial.
     * Si está vacío, ningún vendedor lo ve (solo admin).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "special_product_vendors", joinColumns = @JoinColumn(name = "special_product_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private Set<User> allowedVendors = new HashSet<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ================================================
    // Lifecycle
    // ================================================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ================================================
    // Lógica de stock compartido
    // ================================================

    /**
     * ¿Está vinculado a un producto padre?
     */
    public boolean isLinked() {
        return parentProduct != null;
    }

    /**
     * Retorna el stock efectivo:
     * - Si está vinculado → stock del padre
     * - Si es standalone → ownStock
     */
    public Integer getEffectiveStock() {
        if (isLinked()) {
            return parentProduct.getStock();
        }
        return ownStock;
    }

    /**
     * Disminuye stock. Delega al padre si está vinculado.
     */
    public void decreaseStock(int cantidad) {
        if (isLinked()) {
            parentProduct.decreaseStock(cantidad);
        } else {
            if (cantidad <= 0) {
                throw new IllegalArgumentException("Cantidad inválida");
            }
            if (this.ownStock == null || this.ownStock < cantidad) {
                throw new RuntimeException("Stock insuficiente para " + nombre);
            }
            this.ownStock -= cantidad;
        }
    }

    /**
     * Aumenta stock. Delega al padre si está vinculado.
     */
    public void increaseStock(int cantidad) {
        if (isLinked()) {
            parentProduct.increaseStock(cantidad);
        } else {
            if (cantidad <= 0) {
                throw new IllegalArgumentException("Cantidad inválida");
            }
            if (this.ownStock == null) {
                this.ownStock = 0;
            }
            this.ownStock += cantidad;
        }
    }
}
