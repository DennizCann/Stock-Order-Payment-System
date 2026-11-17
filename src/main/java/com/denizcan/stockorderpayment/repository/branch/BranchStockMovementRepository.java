package com.denizcan.stockorderpayment.repository.branch;

import com.denizcan.stockorderpayment.domain.branch.BranchStockMovement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchStockMovementRepository extends JpaRepository<BranchStockMovement, Long> {

    Optional<BranchStockMovement> findBySaleId(UUID saleId);

    @EntityGraph(attributePaths = {"stock.branch", "stock.product", "actor"})
    List<BranchStockMovement> findTop100ByOrderByCreatedAtDescIdDesc();
}
