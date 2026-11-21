package com.denizcan.stockorderpayment.repository.order;

import com.denizcan.stockorderpayment.domain.order.Order;
import com.denizcan.stockorderpayment.domain.order.OrderSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id=:id")
    Optional<Order> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    Optional<Order> findBySourceAndExternalOrderId(OrderSource source, String externalOrderId);
    List<Order> findAllByOrderByCreatedAtDesc();
}
