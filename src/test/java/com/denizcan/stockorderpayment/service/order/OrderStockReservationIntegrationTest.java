package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.inventory.InventoryTransaction;
import com.denizcan.stockorderpayment.domain.inventory.InventoryTransactionType;
import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.domain.stock.Stock;
import com.denizcan.stockorderpayment.exception.InsufficientStockException;
import com.denizcan.stockorderpayment.repository.inventory.InventoryTransactionRepository;
import com.denizcan.stockorderpayment.repository.order.OrderRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderItemRequest;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderRequest;
import com.denizcan.stockorderpayment.web.order.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderStockReservationIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Test
    void create_shouldReserveStockForMultipleItems() {
        Product laptop = persistProduct("Laptop", "100.00");
        Product mouse = persistProduct("Mouse", "25.00");
        persistStock(laptop.getId(), 50);
        persistStock(mouse.getId(), 30);

        long ordersBefore = orderRepository.count();

        OrderResponse response = orderService.create(new CreateOrderRequest(
                1L,
                List.of(
                        new CreateOrderItemRequest(laptop.getId(), 5),
                        new CreateOrderItemRequest(mouse.getId(), 10)
                )
        ));

        assertThat(response.id()).isNotNull();
        assertThat(response.items()).hasSize(2);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);

        Stock laptopStock = stockRepository.findByProductId(laptop.getId()).orElseThrow();
        Stock mouseStock = stockRepository.findByProductId(mouse.getId()).orElseThrow();

        assertThat(laptopStock.getQuantity()).isEqualTo(50);
        assertThat(laptopStock.getReservedQuantity()).isEqualTo(5);
        assertThat(laptopStock.getAvailableQuantity()).isEqualTo(45);

        assertThat(mouseStock.getQuantity()).isEqualTo(30);
        assertThat(mouseStock.getReservedQuantity()).isEqualTo(10);
        assertThat(mouseStock.getAvailableQuantity()).isEqualTo(20);

        List<InventoryTransaction> laptopTx =
                inventoryTransactionRepository.findByProductIdOrderByCreatedAtDesc(laptop.getId());
        assertThat(laptopTx).anyMatch(tx ->
                tx.getType() == InventoryTransactionType.RESERVATION
                        && tx.getPreviousQuantity() == 50
                        && tx.getNewQuantity() == 45
                        && tx.getQuantityChange() == -5
        );
    }

    @Test
    void create_shouldLeaveCorrectAvailableStockAfterReservation() {
        Product product = persistProduct("Keyboard", "40.00");
        persistStock(product.getId(), 20);

        orderService.create(new CreateOrderRequest(
                2L,
                List.of(new CreateOrderItemRequest(product.getId(), 7))
        ));

        Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(20);
        assertThat(stock.getReservedQuantity()).isEqualTo(7);
        assertThat(stock.getAvailableQuantity()).isEqualTo(13);
    }

    @Test
    void create_shouldFail_whenStockIsInsufficient() {
        Product product = persistProduct("Monitor", "200.00");
        persistStock(product.getId(), 3);
        long ordersBefore = orderRepository.count();

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(
                3L,
                List.of(new CreateOrderItemRequest(product.getId(), 5))
        ))).isInstanceOf(InsufficientStockException.class);

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);

        Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(stock.getAvailableQuantity()).isEqualTo(3);
        assertThat(inventoryTransactionRepository.findByProductIdOrderByCreatedAtDesc(product.getId()))
                .noneMatch(tx -> tx.getType() == InventoryTransactionType.RESERVATION);
    }

    @Test
    void create_shouldRollbackAllReservations_whenOneItemFails() {
        Product available = persistProduct("Available Item", "10.00");
        Product scarce = persistProduct("Scarce Item", "15.00");
        persistStock(available.getId(), 100);
        persistStock(scarce.getId(), 2);

        long ordersBefore = orderRepository.count();
        long reservationsBefore = countReservations(available.getId()) + countReservations(scarce.getId());

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(
                4L,
                List.of(
                        new CreateOrderItemRequest(available.getId(), 20),
                        new CreateOrderItemRequest(scarce.getId(), 5)
                )
        ))).isInstanceOf(InsufficientStockException.class);

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);

        Stock availableStock = stockRepository.findByProductId(available.getId()).orElseThrow();
        Stock scarceStock = stockRepository.findByProductId(scarce.getId()).orElseThrow();

        assertThat(availableStock.getReservedQuantity()).isZero();
        assertThat(availableStock.getAvailableQuantity()).isEqualTo(100);
        assertThat(scarceStock.getReservedQuantity()).isZero();
        assertThat(scarceStock.getAvailableQuantity()).isEqualTo(2);

        long reservationsAfter = countReservations(available.getId()) + countReservations(scarce.getId());
        assertThat(reservationsAfter).isEqualTo(reservationsBefore);
    }

    private Product persistProduct(String name, String price) {
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        return productRepository.save(new Product(name, sku, new BigDecimal(price)));
    }

    private void persistStock(Long productId, int quantity) {
        stockRepository.save(new Stock(productId, quantity));
    }

    private long countReservations(Long productId) {
        return inventoryTransactionRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(tx -> tx.getType() == InventoryTransactionType.RESERVATION)
                .count();
    }
}
