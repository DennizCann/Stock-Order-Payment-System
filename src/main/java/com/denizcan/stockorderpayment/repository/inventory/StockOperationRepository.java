package com.denizcan.stockorderpayment.repository.inventory;
import com.denizcan.stockorderpayment.domain.inventory.StockOperation;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface StockOperationRepository extends JpaRepository<StockOperation, UUID> {
    @EntityGraph(attributePaths = "lines") Optional<StockOperation> findById(UUID id);
    @Query("select o.id from StockOperation o order by o.createdAt desc, o.id desc")
    List<UUID> recentIds(org.springframework.data.domain.Pageable page);
    @Query("select o.id from StockOperation o where o.sourceBranchId = :branch or o.targetBranchId = :branch order by o.createdAt desc, o.id desc")
    List<UUID> recentIdsForBranch(@org.springframework.data.repository.query.Param("branch") Long branch, org.springframework.data.domain.Pageable page);
    @EntityGraph(attributePaths = "lines") List<StockOperation> findAllByIdInOrderByCreatedAtDescIdDesc(Collection<UUID> ids);
}
