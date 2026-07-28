package com.denizcan.stockorderpayment.web.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "User id is required")
        Long userId,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<CreateOrderItemRequest> items
) {
}
