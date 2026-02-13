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
    // CAMPOS PARA GESTIÓN DE INVENTARIO
    // ==========================================
    @Column(name = "cantidad_descontada")
    @Builder.Default
    private Integer cantidadDescontada = 0;

    @Column(name = "cantidad_pendiente")
    @Builder.Default
    private Integer cantidadPendiente = 0;

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

    // ✅ NUEVO: Identificador único para instancia de promoción
    // Permite diferenciar múltiples instancias de la misma promoción
    @Column(name = "promotion_instance_id")
    private UUID promotionInstanceId;

    // ✅ NUEVO: Precio fijo de la promoción guardado en el item
    // Evita que al editar se recalcule la promo como suma de productos
    @Column(name = "promotion_pack_price", precision = 12, scale = 2)
    private BigDecimal promotionPackPrice;

    // ✅ NUEVO: Índice ordinal para promociones duplicadas
    // Sirve para ordenar y diferenciar múltiples del mismo tipo (Promo A #1, Promo
    // A #2)
    @Column(name = "promotion_group_index")
    private Integer promotionGroupIndex;

    // ==========================================
    // CAMPOS PARA BONIFICADOS Y FLETE
    // ==========================================
    @Column(name = "is_bonified")
    @Builder.Default
    private Boolean isBonified = false;

    @Column(name = "is_freight_item")
    @Builder.Default
    private Boolean isFreightItem = false;

    // ==========================================
    // CAMPOS PARA PRODUCTOS ESPECIALES
    // ==========================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "special_product_id")
    private SpecialProduct specialProduct;

    @PrePersist
    @PreUpdate
    public void calcularSubTotal() {
        // ✅ CRÍTICO: Si es item de promoción con precio fijo, NO recalcular
        // Esto preserva el packPrice definido en la promoción
        if (Boolean.TRUE.equals(isPromotionItem) && promotionPackPrice != null) {
            this.subTotal = promotionPackPrice;
            return;
        }

        // Para items normales o si no hay promocionPackPrice: calcular normalmente
        if (subTotal == null && precioUnitario != null && cantidad != null) {
            this.subTotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        }
    }

    // ==========================================
    // CAMPOS PARA PROMOCIONES ESPECIALES (Vinculadas o Standalone)
    // ==========================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "special_promotion_id")
    private SpecialPromotion specialPromotion;

    public OrderItem(Product product, Integer cantidad) {
        this.product = product;
        this.cantidad = cantidad;
        this.precioUnitario = product.getPrecio();
        this.subTotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));

        // Inicializar valores por defecto para evitar nulls
        this.outOfStock = false;
        this.isPromotionItem = false;
        this.isFreeItem = false;
        this.cantidadDescontada = 0;
        this.cantidadPendiente = 0;
    }

}
