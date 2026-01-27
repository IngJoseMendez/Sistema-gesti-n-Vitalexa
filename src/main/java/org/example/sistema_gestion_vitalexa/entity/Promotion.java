package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sistema_gestion_vitalexa.enums.PromotionType;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad para gestionar promociones.
 * Solo Admin y Owner pueden crear/modificar promociones.
 */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionType type;

    // Cantidad que se debe comprar
    @Column(name = "buy_quantity", nullable = false)
    private Integer buyQuantity;

    // Precio del pack completo (para tipo PACK)
    @Column(name = "pack_price", precision = 12, scale = 2)
    private BigDecimal packPrice;

    // Producto principal de la promoción
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_product_id", nullable = false)
    private Product mainProduct;

    // Items de regalo (Lista de productos surtidos/gratis)
    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<PromotionGiftItem> giftItems = new java.util.ArrayList<>();

    // Control de combinación con descuentos normales
    @Column(name = "allow_stack_with_discounts")
    @Builder.Default
    private Boolean allowStackWithDiscounts = false;

    // Para tipo PACK: si admin debe seleccionar los surtidos
    @Column(name = "requires_assortment_selection")
    @Builder.Default
    private Boolean requiresAssortmentSelection = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Verifica si la promoción está dentro del período de validez
     */
    /**
     * Verifica si la promoción está dentro del período de validez
     */
    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        boolean afterStart = validFrom == null || now.isAfter(validFrom);
        boolean beforeEnd = validUntil == null || now.isBefore(validUntil);
        return active && afterStart && beforeEnd;
    }

    /**
     * @return true si la promoción requiere selección de surtidos (Tipo
     *         ASSORTMENT/BUY_GET_FREE)
     */
    public boolean isAssortment() {
        return this.type == PromotionType.BUY_GET_FREE;
    }

    /**
     * @return true si la promoción tiene regalos fijos (Tipo FIXED/PACK)
     */
    public boolean isFixed() {
        return this.type == PromotionType.PACK;
    }

    /**
     * Método auxiliar para compatibilidad: Retorna el primer producto de regalo.
     */
    public Product getFreeProduct() {
        if (giftItems != null && !giftItems.isEmpty()) {
            return giftItems.get(0).getProduct();
        }
        return null;
    }

    /**
     * Método auxiliar para compatibilidad: Retorna la cantidad del primer producto
     * de regalo.
     */
    public Integer getFreeQuantity() {
        if (giftItems != null && !giftItems.isEmpty()) {
            return giftItems.get(0).getQuantity();
        }
        return 0; // O null, dependiendo de la lógica previa, pero 0 es más seguro para sumas
    }
}
