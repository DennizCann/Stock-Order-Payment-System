package com.denizcan.stockorderpayment.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inventory_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryTransactionType type;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "previous_quantity", nullable = false)
    private int previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private int newQuantity;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public InventoryTransaction(
            Long productId,
            InventoryTransactionType type,
            int quantityChange,
            int previousQuantity,
            int newQuantity,
            String note
    ) {
        this.productId = productId;
        this.type = type;
        this.quantityChange = quantityChange;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.note = note;
        this.createdAt = Instant.now();
    }
}
