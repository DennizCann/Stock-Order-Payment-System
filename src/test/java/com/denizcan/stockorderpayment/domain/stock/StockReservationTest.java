package com.denizcan.stockorderpayment.domain.stock;

import com.denizcan.stockorderpayment.exception.InsufficientStockException;
import com.denizcan.stockorderpayment.exception.InvalidStockReservationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockReservationTest {

    @Test
    void availableQuantity_isTotalMinusReserved() {
        Stock stock = new Stock(1L, 100);
        stock.reserve(30);

        assertThat(stock.getQuantity()).isEqualTo(100);
        assertThat(stock.getReservedQuantity()).isEqualTo(30);
        assertThat(stock.getAvailableQuantity()).isEqualTo(70);
    }

    @Test
    void reserve_shouldIncreaseReservedQuantity() {
        Stock stock = new Stock(1L, 50);

        stock.reserve(20);

        assertThat(stock.getReservedQuantity()).isEqualTo(20);
        assertThat(stock.getAvailableQuantity()).isEqualTo(30);
    }

    @Test
    void reserve_shouldThrow_whenInsufficientAvailable() {
        Stock stock = new Stock(1L, 10);
        stock.reserve(8);

        assertThatThrownBy(() -> stock.reserve(5))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Available: 2")
                .hasMessageContaining("requested: 5");

        assertThat(stock.getReservedQuantity()).isEqualTo(8);
    }

    @Test
    void reserve_shouldThrow_whenAmountNotPositive() {
        Stock stock = new Stock(1L, 10);

        assertThatThrownBy(() -> stock.reserve(0))
                .isInstanceOf(InvalidStockReservationException.class);
        assertThatThrownBy(() -> stock.reserve(-1))
                .isInstanceOf(InvalidStockReservationException.class);
    }

    @Test
    void release_shouldDecreaseReservedQuantityWithoutChangingTotal() {
        Stock stock = new Stock(1L, 40);
        stock.reserve(25);

        stock.release(10);

        assertThat(stock.getQuantity()).isEqualTo(40);
        assertThat(stock.getReservedQuantity()).isEqualTo(15);
        assertThat(stock.getAvailableQuantity()).isEqualTo(25);
    }

    @Test
    void release_shouldThrow_whenReleasingMoreThanReserved() {
        Stock stock = new Stock(1L, 40);
        stock.reserve(5);

        assertThatThrownBy(() -> stock.release(6))
                .isInstanceOf(InvalidStockReservationException.class)
                .hasMessageContaining("Cannot release");
    }

    @Test
    void consumeReserved_shouldDecreaseTotalAndReserved() {
        Stock stock = new Stock(1L, 100);
        stock.reserve(40);

        stock.consumeReserved(15);

        assertThat(stock.getQuantity()).isEqualTo(85);
        assertThat(stock.getReservedQuantity()).isEqualTo(25);
        assertThat(stock.getAvailableQuantity()).isEqualTo(60);
    }

    @Test
    void consumeReserved_shouldThrow_whenConsumingMoreThanReserved() {
        Stock stock = new Stock(1L, 100);
        stock.reserve(10);

        assertThatThrownBy(() -> stock.consumeReserved(11))
                .isInstanceOf(InvalidStockReservationException.class)
                .hasMessageContaining("Cannot consume");

        assertThat(stock.getQuantity()).isEqualTo(100);
        assertThat(stock.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    void setQuantity_shouldRejectValueBelowReserved() {
        Stock stock = new Stock(1L, 50);
        stock.reserve(20);

        assertThatThrownBy(() -> stock.setQuantity(15))
                .isInstanceOf(InvalidStockReservationException.class);

        assertThat(stock.getQuantity()).isEqualTo(50);
    }

    @Test
    void setQuantity_shouldRejectNegative() {
        Stock stock = new Stock(1L, 10);

        assertThatThrownBy(() -> stock.setQuantity(-1))
                .isInstanceOf(InvalidStockReservationException.class);
    }

    @Test
    void constructor_shouldRejectNegativeQuantity() {
        assertThatThrownBy(() -> new Stock(1L, -5))
                .isInstanceOf(InvalidStockReservationException.class);
    }
}
