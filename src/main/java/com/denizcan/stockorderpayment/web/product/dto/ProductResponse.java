package com.denizcan.stockorderpayment.web.product.dto;

import com.denizcan.stockorderpayment.domain.product.Product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        BigDecimal price
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getPrice()
        );
    }
}
