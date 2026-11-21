package com.denizcan.stockorderpayment.repository.inventory;

import com.denizcan.stockorderpayment.domain.inventory.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<InventoryTransaction> findTop100ByOrderByCreatedAtDescIdDesc();
    List<InventoryTransaction> findTop100ByOperationIdIsNullAndTypeNotInAndQuantityChangeNotOrderByCreatedAtDescIdDesc(
            java.util.Collection<com.denizcan.stockorderpayment.domain.inventory.InventoryTransactionType> excluded, int change);
}
