package com.denizcan.stockorderpayment.repository.order;
import com.denizcan.stockorderpayment.domain.order.StockPublication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface StockPublicationRepository extends JpaRepository<StockPublication,UUID> {
    @Override @EntityGraph(attributePaths="lines") Optional<StockPublication> findById(UUID id);
    @Query("select p.id from StockPublication p order by p.createdAt desc,p.id desc") List<UUID> recentIds(org.springframework.data.domain.Pageable page);
    @EntityGraph(attributePaths="lines") List<StockPublication> findAllByIdInOrderByCreatedAtDesc(List<UUID> ids);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from StockPublication p where p.id=:id")
    Optional<StockPublication> lock(@Param("id") UUID id);
    @Query("select p.id from StockPublication p where p.activeKey is not null") List<UUID> activeIds();
    boolean existsByConnectionKeyAndFingerprintAndCreatedAtAfter(String key,String fingerprint,java.time.Instant after);
    @Query(value="""
        select barcode, quantity from (
          select l.barcode, l.quantity, p.state, row_number() over(partition by l.barcode order by p.created_at desc,p.id desc) as rn
          from stock_publications p join stock_publication_lines l on l.publication_id=p.id
          where p.connection_key=:key
        ) confirmed where rn=1 and state='SUCCESS'
        """,nativeQuery=true)
    List<Object[]> confirmedQuantities(@Param("key") String key);
}
