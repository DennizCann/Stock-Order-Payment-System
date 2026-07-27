package com.denizcan.stockorderpayment.web.inventory.dto;

import com.denizcan.stockorderpayment.domain.inventory.InventoryTransaction;
import com.denizcan.stockorderpayment.domain.inventory.InventoryTransactionType;

import java.time.Instant;

public record InventoryTransactionResponse(
        Long id,
        Long productId,
        InventoryTransactionType type,
        int quantityChange,
        int previousQuantity,
        int newQuantity,
        String note,
        Instant createdAt
) {

    public static InventoryTransactionResponse from(InventoryTransaction tx) {
        return new InventoryTransactionResponse(
                tx.getId(),
                tx.getProductId(),
                tx.getType(),
                tx.getQuantityChange(),
                tx.getPreviousQuantity(),
                tx.getNewQuantity(),
                tx.getNote(),
                tx.getCreatedAt()
        );
    }
}
