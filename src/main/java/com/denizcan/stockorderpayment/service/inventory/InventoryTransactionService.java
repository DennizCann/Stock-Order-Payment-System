package com.denizcan.stockorderpayment.service.inventory;

import com.denizcan.stockorderpayment.domain.inventory.InventoryTransaction;
import com.denizcan.stockorderpayment.domain.inventory.InventoryTransactionType;
import com.denizcan.stockorderpayment.repository.inventory.InventoryTransactionRepository;
import com.denizcan.stockorderpayment.web.inventory.dto.InventoryTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryTransactionService {

    private final InventoryTransactionRepository inventoryTransactionRepository;

    @Transactional
    public void record(
            Long productId,
            InventoryTransactionType type,
            int previousQuantity,
            int newQuantity,
            String note
    ) {
        int change = newQuantity - previousQuantity;
        InventoryTransaction tx = new InventoryTransaction(
                productId,
                type,
                change,
                previousQuantity,
                newQuantity,
                note
        );
        inventoryTransactionRepository.save(tx);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> findByProductId(Long productId) {
        return inventoryTransactionRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(InventoryTransactionResponse::from)
                .toList();
    }
}
