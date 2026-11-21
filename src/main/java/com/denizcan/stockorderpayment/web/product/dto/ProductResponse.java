package com.denizcan.stockorderpayment.web.product.dto;

import com.denizcan.stockorderpayment.domain.product.Product;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        BigDecimal price,
        long quantity,
        boolean active,
        List<Tag> tags
) {

    public record Tag(Long id, String name, String value) {}

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getPrice(),
                product.getQuantity(),
                product.isActive(),
                product.getTags().stream().map(t -> new Tag(t.getId(), t.getName(), t.getValue())).toList()
        );
    }
}
