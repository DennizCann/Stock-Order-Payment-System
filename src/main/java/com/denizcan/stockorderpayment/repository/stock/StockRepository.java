package com.denizcan.stockorderpayment.repository.stock;

import com.denizcan.stockorderpayment.domain.stock.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    boolean existsByProductId(Long productId);

    Optional<Stock> findByProductId(Long productId);
}
