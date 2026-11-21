package com.denizcan.stockorderpayment.repository.branch;

import com.denizcan.stockorderpayment.domain.branch.BranchStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BranchStockRepository extends JpaRepository<BranchStock, Long> {

    @EntityGraph(attributePaths = "product")
    List<BranchStock> findAllByBranchIdOrderByProductNameAsc(Long branchId);
    boolean existsByBranchIdAndQuantityGreaterThan(Long branchId, int quantity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BranchStock s where s.branch.id = :branchId and s.product.id = :productId")
    Optional<BranchStock> findForUpdate(@Param("branchId") Long branchId, @Param("productId") Long productId);
}
