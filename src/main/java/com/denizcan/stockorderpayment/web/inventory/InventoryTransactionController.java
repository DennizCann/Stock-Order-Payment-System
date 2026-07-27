package com.denizcan.stockorderpayment.web.inventory;

import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.service.inventory.InventoryTransactionService;
import com.denizcan.stockorderpayment.web.inventory.dto.InventoryTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/inventory-transactions")
@RequiredArgsConstructor
public class InventoryTransactionController {

    private final ProductRepository productRepository;
    private final InventoryTransactionService inventoryTransactionService;

    @GetMapping
    public List<InventoryTransactionResponse> findByProductId(@PathVariable Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return inventoryTransactionService.findByProductId(productId);
    }
}
