package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.Order;
import com.denizcan.stockorderpayment.domain.order.OrderStatus;
import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.exception.InvalidOrderException;
import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.repository.order.OrderRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderItemRequest;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderRequest;
import com.denizcan.stockorderpayment.web.order.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void create_shouldPersistOrderWithCreatedStatusAndCalculatedTotals() {
        Product laptop = productWithId(1L, "Laptop", "LAP-1", "100.00");
        Product mouse = productWithId(2L, "Mouse", "MOU-1", "25.00");

        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(laptop, mouse));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
                7L,
                List.of(
                        new CreateOrderItemRequest(1L, 2),
                        new CreateOrderItemRequest(2L, 3)
                )
        );

        OrderResponse response = orderService.create(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("275.00");
        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.totalAmount()).isEqualByComparingTo("275.00");
        assertThat(response.items()).hasSize(2);
    }

    @Test
    void create_shouldThrow_whenProductMissing() {
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of());

        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(99L, 1))
        );

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ProductNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_shouldThrow_whenQuantityInvalid() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(1L, 0))
        );

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Quantity");

        verify(productRepository, never()).findAllById(anyCollection());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_shouldMergeDuplicateProductLines() {
        Product laptop = productWithId(1L, "Laptop", "LAP-1", "50.00");
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(laptop));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(
                        new CreateOrderItemRequest(1L, 2),
                        new CreateOrderItemRequest(1L, 3)
                )
        );

        OrderResponse response = orderService.create(request);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().quantity()).isEqualTo(5);
        assertThat(response.totalAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void create_shouldUseCurrentProductPriceForLineTotals() {
        Product product = productWithId(5L, "Keyboard", "KEY-1", "40.00");
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.create(new CreateOrderRequest(
                3L,
                List.of(new CreateOrderItemRequest(5L, 4))
        ));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("40.00");
        assertThat(response.items().getFirst().lineTotal()).isEqualByComparingTo("160.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("160.00");
    }

    private static Product productWithId(Long id, String name, String sku, String price) {
        Product product = new Product(name, sku, new BigDecimal(price));
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return product;
    }
}
