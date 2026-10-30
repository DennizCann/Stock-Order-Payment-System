package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.domain.stock.Stock;
import com.denizcan.stockorderpayment.exception.InsufficientStockException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderItemRequest;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentOrderIntegrationTest {
    @Autowired OrderService service;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired UserRepository users;

    @Test
    void onlyOneConcurrentOrderCanReserveLastUnit() throws Exception {
        Product product = products.save(new Product("Last unit", UUID.randomUUID().toString(), BigDecimal.TEN));
        stocks.save(new Stock(product.getId(), 1));
        Long accountId = users.findByUsername("user").orElseThrow().getId();
        CreateOrderRequest request = new CreateOrderRequest(accountId,
                List.of(new CreateOrderItemRequest(product.getId(), 1)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> place = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
            try {
                service.create(request);
                return true;
            } catch (InsufficientStockException expected) {
                return false;
            }
        };
        try {
            Future<Boolean> first = pool.submit(place);
            Future<Boolean> second = pool.submit(place);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            Stock stock = stocks.findByProductId(product.getId()).orElseThrow();
            assertThat(stock.getReservedQuantity()).isEqualTo(1);
            assertThat(stock.getAvailableQuantity()).isZero();
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
