package com.denizcan.stockorderpayment.web.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockQuantityRequest(
        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity cannot be negative")
        Integer quantity,
        @Min(0) Long version
) {
    public StockQuantityRequest(Integer quantity) { this(quantity, null); }
}
