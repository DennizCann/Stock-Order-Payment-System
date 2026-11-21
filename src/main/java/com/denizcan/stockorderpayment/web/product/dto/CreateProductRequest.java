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
        @Size(max = 255)
        String name,

        @NotBlank(message = "SKU is required")
        @Size(max = 64, message = "SKU must be at most 64 characters")
        String sku,

        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @jakarta.validation.constraints.Digits(integer=17,fraction=2)
        BigDecimal price,
        @PositiveOrZero Integer quantity,
        List<@NotNull @Valid ProductTagRequest> tags
) {
    public CreateProductRequest {
        name = name == null ? null : name.strip();
        sku = sku == null ? null : sku.strip();
    }
    public CreateProductRequest(String name, String sku, BigDecimal price) {
        this(name, sku, price, null, List.of());
    }
}
