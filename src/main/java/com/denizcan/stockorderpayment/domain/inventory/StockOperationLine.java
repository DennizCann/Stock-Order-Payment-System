package com.denizcan.stockorderpayment.domain.inventory;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "stock_operation_lines") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockOperationLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "product_name", nullable = false) private String productName;
    @Column(nullable = false, length = 64) private String sku;
    @Column(nullable = false) private int quantity;
    @Column(name = "source_remaining") private Integer sourceRemaining;
    @Column(name = "target_remaining") private Integer targetRemaining;
    public StockOperationLine(Long productId, String productName, String sku, int quantity,
                              Integer sourceRemaining, Integer targetRemaining) {
        this.productId = productId; this.productName = productName; this.sku = sku; this.quantity = quantity;
        this.sourceRemaining = sourceRemaining; this.targetRemaining = targetRemaining;
    }
}
