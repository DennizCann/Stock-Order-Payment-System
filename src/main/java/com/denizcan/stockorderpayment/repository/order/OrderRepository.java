package com.denizcan.stockorderpayment.repository.order;

import com.denizcan.stockorderpayment.domain.order.Order;
import com.denizcan.stockorderpayment.domain.order.OrderSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    Optional<Order> findBySourceAndExternalOrderId(OrderSource source, String externalOrderId);
    List<Order> findAllByOrderByCreatedAtDesc();
}
