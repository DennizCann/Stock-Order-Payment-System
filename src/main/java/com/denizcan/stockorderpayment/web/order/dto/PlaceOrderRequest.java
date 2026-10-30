package com.denizcan.stockorderpayment.web.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PlaceOrderRequest(
        @NotEmpty(message = "Order must contain at least one item")
        List<@NotNull @Valid CreateOrderItemRequest> items
) {
}
