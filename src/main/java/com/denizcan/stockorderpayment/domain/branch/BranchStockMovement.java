package com.denizcan.stockorderpayment.domain.branch;

import com.denizcan.stockorderpayment.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch_stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchStockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private BranchStock stock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(name = "previous_quantity", nullable = false)
    private int previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private int newQuantity;

    @Column(name = "sale_id", unique = true)
    private UUID saleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(length = 100)
    private String reason;

    @Column(length = 255)
    private String note;

    public void describe(String reason, String note) {
        this.reason = reason;
        this.note = note;
    }

    public BranchStockMovement(BranchStock stock, User actor, int previousQuantity, UUID saleId) {
        this.stock = stock;
        this.actor = actor;
        this.previousQuantity = previousQuantity;
        this.newQuantity = stock.getQuantity();
        this.saleId = saleId;
        this.createdAt = Instant.now();
    }

    public int getChange() {
        return newQuantity - previousQuantity;
    }
}
