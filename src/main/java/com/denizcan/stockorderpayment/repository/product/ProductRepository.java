package com.denizcan.stockorderpayment.repository.product;

import com.denizcan.stockorderpayment.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);
}
