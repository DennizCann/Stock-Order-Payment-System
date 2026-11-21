package com.denizcan.stockorderpayment.web.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateProductRequest(
        @NotBlank(message = "Product name is required")
        String name,

        @NotBlank(message = "SKU is required")
        @Size(max = 64, message = "SKU must be at most 64 characters")
        String sku,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal price,
        @PositiveOrZero Integer quantity,
        List<@NotNull @Valid ProductTagRequest> tags
) {
    public CreateProductRequest(String name, String sku, BigDecimal price) {
        this(name, sku, price, null, List.of());
    }
}
