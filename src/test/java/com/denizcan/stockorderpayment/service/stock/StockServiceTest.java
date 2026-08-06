package com.denizcan.stockorderpayment.service.stock;

import com.denizcan.stockorderpayment.domain.inventory.InventoryTransactionType;
import com.denizcan.stockorderpayment.domain.stock.Stock;
import com.denizcan.stockorderpayment.exception.InsufficientStockException;
import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.exception.StockAlreadyExistsException;
import com.denizcan.stockorderpayment.exception.StockNotFoundException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.service.inventory.InventoryTransactionService;
import com.denizcan.stockorderpayment.web.inventory.dto.StockMovementRequest;
import com.denizcan.stockorderpayment.web.stock.dto.CreateStockRequest;
import com.denizcan.stockorderpayment.web.stock.dto.StockResponse;
import com.denizcan.stockorderpayment.web.stock.dto.UpdateStockRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private InventoryTransactionService inventoryTransactionService;

    @InjectMocks
    private StockService stockService;

    @Test
    void create_shouldPersistStock_whenProductExistsAndStockMissing() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(stockRepository.existsByProductId(1L)).thenReturn(false);
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockResponse response = stockService.create(1L, new CreateStockRequest(100));

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.quantity()).isEqualTo(100);
        verify(stockRepository).save(any(Stock.class));
        verify(inventoryTransactionService).record(
                eq(1L),
                eq(InventoryTransactionType.INITIAL),
                eq(0),
                eq(100),
                anyString()
        );
    }

    @Test
    void create_shouldThrow_whenProductMissing() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> stockService.create(99L, new CreateStockRequest(10)))
                .isInstanceOf(ProductNotFoundException.class);

        verify(stockRepository, never()).save(any());
        verify(inventoryTransactionService, never()).record(anyLong(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void create_shouldThrow_whenStockAlreadyExists() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(stockRepository.existsByProductId(1L)).thenReturn(true);

        assertThatThrownBy(() -> stockService.create(1L, new CreateStockRequest(10)))
                .isInstanceOf(StockAlreadyExistsException.class);
    }

    @Test
    void findByProductId_shouldThrow_whenMissing() {
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.findByProductId(1L))
                .isInstanceOf(StockNotFoundException.class);
    }

    @Test
    void updateQuantity_shouldSetNewValueAndRecordAdjustment() {
        Stock stock = new Stock(1L, 50);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        StockResponse response = stockService.updateQuantity(1L, new UpdateStockRequest(80));

        assertThat(response.quantity()).isEqualTo(80);
        verify(inventoryTransactionService).record(
                eq(1L),
                eq(InventoryTransactionType.ADJUSTMENT),
                eq(50),
                eq(80),
                anyString()
        );
    }

    @Test
    void stockOut_shouldThrow_whenInsufficient() {
        Stock stock = new Stock(1L, 5);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> stockService.stockOut(1L, new StockMovementRequest(10, "order")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void stockOut_shouldThrow_whenAvailableIsReducedByReservation() {
        Stock stock = new Stock(1L, 10);
        stock.reserve(8);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> stockService.stockOut(1L, new StockMovementRequest(5, "order")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void stockIn_shouldIncreaseQuantity() {
        Stock stock = new Stock(1L, 20);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        StockResponse response = stockService.stockIn(1L, new StockMovementRequest(15, "purchase"));

        assertThat(response.quantity()).isEqualTo(35);
        verify(inventoryTransactionService).record(
                eq(1L),
                eq(InventoryTransactionType.STOCK_IN),
                eq(20),
                eq(35),
                eq("purchase")
        );
    }

    @Test
    void reserve_shouldIncreaseReservedAndRecordReservation() {
        Stock stock = new Stock(1L, 20);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        StockResponse response = stockService.reserve(1L, 7, "Reserved for order 9");

        assertThat(response.quantity()).isEqualTo(20);
        assertThat(response.reservedQuantity()).isEqualTo(7);
        assertThat(response.availableQuantity()).isEqualTo(13);
        verify(inventoryTransactionService).record(
                eq(1L),
                eq(InventoryTransactionType.RESERVATION),
                eq(20),
                eq(13),
                eq("Reserved for order 9")
        );
    }

    @Test
    void reserve_shouldThrow_whenInsufficientAvailable() {
        Stock stock = new Stock(1L, 5);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> stockService.reserve(1L, 6, "order"))
                .isInstanceOf(InsufficientStockException.class);
        verify(inventoryTransactionService, never()).record(anyLong(), any(), anyInt(), anyInt(), anyString());
    }
}
