package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private BigDecimal subTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // ==========================================
    // CAMPOS PARA PRODUCTOS SIN STOCK
    // ==========================================
    @Column(name = "out_of_stock")
    @Builder.Default
    private Boolean outOfStock = false;

    @Column(name = "estimated_arrival_date")
    private LocalDate estimatedArrivalDate;

    @Column(name = "estimated_arrival_note", length = 500)
    private String estimatedArrivalNote;

    // ==========================================
    // CAMPOS PARA PROMOCIONES
    // ==========================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(name = "is_promotion_item")
    @Builder.Default
    private Boolean isPromotionItem = false;

    @Column(name = "is_free_item")
    @Builder.Default
    private Boolean isFreeItem = false;

    @PrePersist
    @PreUpdate
    public void calcularSubTotal() {
        // Solo calcular si subTotal no está establecido
        // Esto permite que las promociones establezcan su propio subTotal
        if (subTotal == null && precioUnitario != null && cantidad != null) {
            this.subTotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        }
    }

    public OrderItem(Product product, Integer cantidad) {
        this.product = product;
        this.cantidad = cantidad;
        this.precioUnitario = product.getPrecio();
        this.subTotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));

        // Inicializar valores por defecto para evitar nulls
        this.outOfStock = false;
        this.isPromotionItem = false;
        this.isFreeItem = false;
    }

}
