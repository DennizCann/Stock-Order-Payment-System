package com.denizcan.stockorderpayment.repository.order;
import com.denizcan.stockorderpayment.domain.order.OrderStateChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OrderStateChangeRepository extends JpaRepository<OrderStateChange,Long> {
    List<OrderStateChange> findTop50ByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);
}
