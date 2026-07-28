package com.denizcan.stockorderpayment.domain.order;

import com.denizcan.stockorderpayment.repository.order.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OrderPersistenceTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldPersistOrderWithItemsAndRecalculateTotal() {
        Order order = new Order(1L);
        order.addItem(new OrderItem(10L, 2, new BigDecimal("50.00")));
        order.addItem(new OrderItem(20L, 1, new BigDecimal("30.00")));

        Order saved = orderRepository.saveAndFlush(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("130.00");
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Order> reloaded = orderRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getItems()).hasSize(2);
        assertThat(reloaded.get().getItems().get(0).getLineTotal())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void shouldUpdateStatusAndTouchUpdatedAt() {
        Order order = new Order(2L);
        order.addItem(new OrderItem(11L, 1, new BigDecimal("10.00")));
        Order saved = orderRepository.saveAndFlush(order);

        var previousUpdatedAt = saved.getUpdatedAt();
        saved.changeStatus(OrderStatus.PAID);
        Order updated = orderRepository.saveAndFlush(saved);

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(previousUpdatedAt);
    }
}
