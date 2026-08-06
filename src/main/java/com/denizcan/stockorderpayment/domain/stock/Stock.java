package com.denizcan.stockorderpayment.domain.stock;

import com.denizcan.stockorderpayment.exception.InsufficientStockException;
import com.denizcan.stockorderpayment.exception.InvalidStockReservationException;
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

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    public Stock(Long productId, int quantity) {
        if (quantity < 0) {
            throw new InvalidStockReservationException("Quantity cannot be negative");
        }
        this.productId = productId;
        this.quantity = quantity;
        this.reservedQuantity = 0;
    }

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new InvalidStockReservationException("Quantity cannot be negative");
        }
        if (quantity < reservedQuantity) {
            throw new InvalidStockReservationException(
                    "Quantity cannot be less than reserved quantity (" + reservedQuantity + ")"
            );
        }
        this.quantity = quantity;
    }

    public void reserve(int amount) {
        requirePositiveAmount(amount);
        if (getAvailableQuantity() < amount) {
            throw new InsufficientStockException(productId, getAvailableQuantity(), amount);
        }
        reservedQuantity += amount;
    }

    public void release(int amount) {
        requirePositiveAmount(amount);
        if (reservedQuantity < amount) {
            throw new InvalidStockReservationException(
                    "Cannot release " + amount + " units; only " + reservedQuantity + " reserved"
            );
        }
        reservedQuantity -= amount;
    }

    public void consumeReserved(int amount) {
        requirePositiveAmount(amount);
        if (reservedQuantity < amount) {
            throw new InvalidStockReservationException(
                    "Cannot consume " + amount + " units; only " + reservedQuantity + " reserved"
            );
        }
        reservedQuantity -= amount;
        quantity -= amount;
    }

    private static void requirePositiveAmount(int amount) {
        if (amount <= 0) {
            throw new InvalidStockReservationException("Amount must be greater than zero");
        }
    }
}
