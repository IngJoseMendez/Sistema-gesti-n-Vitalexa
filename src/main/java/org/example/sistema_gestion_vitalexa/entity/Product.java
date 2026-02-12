package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter

public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private BigDecimal precio;

    private Integer stock;

    private String imageUrl;

    private boolean active = true;

    @Column(name = "is_hidden")
    @Builder.Default
    private boolean isHidden = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // reducir stock metodo
    public void decreaseStock(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("Cantidad inválida");
        }
        // PERMITIMOS STOCK NEGATIVO - SE ELIMINA LA VALIDACIÓN DE STOCK INSUFICIENTE
        this.stock -= cantidad;
    }

    // aumentar stock metodo
    public void increaseStock(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("Cantidad inválida");
        }
        if (this.stock == null) {
            this.stock = 0;
        }
        this.stock += cantidad;
    }

    @Column(name = "reorder_point")
    private Integer reorderPoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_tag_id")
    private ProductTag tag;

    /**
     * Productos especiales vinculados a este producto (hard link).
     * Se usa para saber cuántos especiales comparten el stock de este producto.
     */
    @OneToMany(mappedBy = "parentProduct", fetch = FetchType.LAZY)
    @Builder.Default
    private List<SpecialProduct> specialProducts = new ArrayList<>();
}
