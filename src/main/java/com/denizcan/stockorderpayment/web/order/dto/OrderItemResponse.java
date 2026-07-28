package com.denizcan.stockorderpayment.web.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
