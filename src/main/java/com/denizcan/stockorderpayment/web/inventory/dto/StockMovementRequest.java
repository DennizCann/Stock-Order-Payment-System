package com.denizcan.stockorderpayment.web.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockMovementRequest(
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @Size(max = 255, message = "Note must be at most 255 characters")
        String note
) {
}
