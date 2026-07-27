package com.denizcan.stockorderpayment.domain.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "stock",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_product_id",
                columnNames = "product_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Setter
    @Column(nullable = false)
    private int quantity;

    public Stock(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}
