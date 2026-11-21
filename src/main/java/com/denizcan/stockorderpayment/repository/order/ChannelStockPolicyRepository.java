package com.denizcan.stockorderpayment.repository.order;
import com.denizcan.stockorderpayment.domain.order.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ChannelStockPolicyRepository extends JpaRepository<ChannelStockPolicy,Long> {
    Optional<ChannelStockPolicy> findBySourceAndProductId(OrderSource source,Long productId);
    List<ChannelStockPolicy> findAllBySourceOrderByProductIdAsc(OrderSource source);
}
