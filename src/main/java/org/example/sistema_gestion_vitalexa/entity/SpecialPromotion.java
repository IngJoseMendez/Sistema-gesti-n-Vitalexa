package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sistema_gestion_vitalexa.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "special_promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PromotionType type;

    // Cantidad que se debe comprar (override or standalone)
    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    // Precio del pack completo (override or standalone)
    @Column(name = "pack_price", precision = 12, scale = 2)
    private BigDecimal packPrice;

    // Producto principal (solo para standalone o si se quiere cambiar en el
    // override)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_product_id")
    private Product mainProduct;

    @Builder.Default
    private boolean active = true;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    /**
     * Promoción padre (hard link). Null = standalone.
     * Si existe, se heredan comportamientos salvo que se sobreescriban.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_promotion_id")
    private Promotion parentPromotion;

    /**
     * Vendedores que pueden ver y usar esta promoción especial.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "special_promotion_vendors", joinColumns = @JoinColumn(name = "special_promotion_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
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
    // Logic
    // ================================================

    public boolean isLinked() {
        return parentPromotion != null;
    }

    // Helpers to resolve effective values
    public Integer getEffectiveBuyQuantity() {
        if (buyQuantity != null)
            return buyQuantity;
        if (isLinked())
            return parentPromotion.getBuyQuantity();
        return 0;
    }

    public BigDecimal getEffectivePackPrice() {
        if (packPrice != null)
            return packPrice;
        if (isLinked())
            return parentPromotion.getPackPrice();
        return BigDecimal.ZERO;
    }

    public PromotionType getEffectiveType() {
        if (type != null)
            return type;
        if (isLinked())
            return parentPromotion.getType();
        return null;
    }
}
