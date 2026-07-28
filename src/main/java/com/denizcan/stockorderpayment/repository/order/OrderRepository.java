package com.denizcan.stockorderpayment.repository.order;

import com.denizcan.stockorderpayment.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
