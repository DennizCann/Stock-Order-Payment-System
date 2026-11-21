package com.denizcan.stockorderpayment.repository.product;

import com.denizcan.stockorderpayment.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id=:id")
    Optional<Product> findForUpdate(@Param("id") Long id);

    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.quantity = p.quantity + :delta where p.id = :id and p.active = true and p.quantity + :delta >= 0")
    int adjustQuantity(@Param("id") Long id, @Param("delta") long delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.active = false, p.version = p.version + 1 where p.id = :id and p.active = true and p.quantity = 0")
    int archiveEmpty(@Param("id") Long id);

    @Query("select distinct t.name, t.value from Product p join p.tags t where p.active = true order by t.name, t.value")
    List<Object[]> tagSuggestions();
    Optional<Product> findBySku(String sku);
}
