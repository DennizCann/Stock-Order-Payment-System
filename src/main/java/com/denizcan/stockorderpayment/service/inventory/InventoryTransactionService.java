package com.denizcan.stockorderpayment.service.inventory;

import com.denizcan.stockorderpayment.domain.inventory.InventoryTransaction;
import com.denizcan.stockorderpayment.domain.inventory.InventoryTransactionType;
import com.denizcan.stockorderpayment.repository.inventory.InventoryTransactionRepository;
import com.denizcan.stockorderpayment.web.inventory.dto.InventoryTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@RequiredArgsConstructor
public class InventoryTransactionService {

    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ProductRepository products;

    @Transactional
    public void record(
            Long productId,
            InventoryTransactionType type,
            int previousQuantity,
            int newQuantity,
            String note
    ) {
        recordMovement(productId, type, previousQuantity, newQuantity, note, null);
    }

    @Transactional
    public void recordMovement(Long productId, InventoryTransactionType type, int previousQuantity,
                               int newQuantity, String note, String reason) {
        int change = newQuantity - previousQuantity;
        InventoryTransaction tx = new InventoryTransaction(
                productId,
                type,
                change,
                previousQuantity,
                newQuantity,
                note
        );
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        tx.setActor(authentication == null ? "Sistem" : authentication.getName());
        tx.setReason(reason);
        if (type != InventoryTransactionType.RESERVATION && type != InventoryTransactionType.RELEASE) {
            if (products.adjustQuantity(productId, change) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ürün arşivlenmiş veya stok toplamı geçersiz.");
            }
        }
        inventoryTransactionRepository.save(tx);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> findByProductId(Long productId) {
        return inventoryTransactionRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(InventoryTransactionResponse::from)
                .toList();
    }
}
