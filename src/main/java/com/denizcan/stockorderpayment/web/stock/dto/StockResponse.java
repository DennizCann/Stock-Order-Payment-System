package com.denizcan.stockorderpayment.web.stock.dto;

import com.denizcan.stockorderpayment.domain.stock.Stock;

public record StockResponse(
        Long id,
        Long productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity
) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(
                stock.getId(),
                stock.getProductId(),
                stock.getQuantity(),
                stock.getReservedQuantity(),
                stock.getAvailableQuantity()
        );
    }
}
