package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.OrderSource;
import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.domain.stock.Stock;
import com.denizcan.stockorderpayment.exception.ExternalOrderConflictException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.denizcan.stockorderpayment.web.order.dto.ChannelOrderRequest;
import com.denizcan.stockorderpayment.web.order.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ChannelOrderIntegrationTest {
    @Autowired ChannelOrderService service;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired UserRepository users;

    @ParameterizedTest
    @EnumSource(value = OrderSource.class, names = {"TRENDYOL", "HEPSIBURADA", "N11", "WEBSTORE", "BRANCH"})
    void repeatDeliveryDoesNotReserveStockTwice(OrderSource source) {
        Product product = productWithStock();
        Long account = users.findByUsername("admin").orElseThrow().getId();
        String externalId = UUID.randomUUID().toString();
        ChannelOrderRequest request = request(product, externalId, 2);
        OrderResponse first = service.importOrder(source, account, request);
        OrderResponse repeated = service.importOrder(source, account, request);
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repeated.source()).isEqualTo(source);
        assertThat(stocks.findByProductId(product.getId()).orElseThrow().getReservedQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> service.importOrder(source, account, request(product, externalId, 3)))
                .isInstanceOf(ExternalOrderConflictException.class);
    }

    @Test
    void concurrentDuplicateDeliveryCreatesOneOrder() throws Exception {
        Product product = productWithStock();
        Long account = users.findByUsername("admin").orElseThrow().getId();
        ChannelOrderRequest request = request(product, UUID.randomUUID().toString(), 2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<OrderResponse> task = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
            return service.importOrder(OrderSource.TRENDYOL, account, request);
        };
        try {
            Future<OrderResponse> first = pool.submit(task);
            Future<OrderResponse> second = pool.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).id()).isEqualTo(second.get(10, TimeUnit.SECONDS).id());
            assertThat(stocks.findByProductId(product.getId()).orElseThrow().getReservedQuantity()).isEqualTo(2);
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    private Product productWithStock() {
        Product product = products.save(new Product("Channel item", UUID.randomUUID().toString(), BigDecimal.TEN));
        stocks.save(new Stock(product.getId(), 10));
        return product;
    }

    private ChannelOrderRequest request(Product product, String externalId, int quantity) {
        return new ChannelOrderRequest(externalId, List.of(new ChannelOrderRequest.Item(product.getSku(), quantity)));
    }
}
