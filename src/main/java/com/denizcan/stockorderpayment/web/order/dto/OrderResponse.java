package com.denizcan.stockorderpayment.web.order.dto;

import com.denizcan.stockorderpayment.domain.order.OrderStatus;
import com.denizcan.stockorderpayment.domain.order.OrderSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items,
        OrderSource source,
        String externalOrderId
) {
}
