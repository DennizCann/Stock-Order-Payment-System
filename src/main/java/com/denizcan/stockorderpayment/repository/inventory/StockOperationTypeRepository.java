package com.denizcan.stockorderpayment.repository.inventory;
import com.denizcan.stockorderpayment.domain.inventory.StockOperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface StockOperationTypeRepository extends JpaRepository<StockOperationType, Long> {
    List<StockOperationType> findAllByOrderByNameAsc();
}
