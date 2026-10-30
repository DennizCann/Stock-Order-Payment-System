package com.denizcan.stockorderpayment.repository.product;

import com.denizcan.stockorderpayment.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);
    Optional<Product> findBySku(String sku);
}
