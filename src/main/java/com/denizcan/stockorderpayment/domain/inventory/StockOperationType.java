package com.denizcan.stockorderpayment.domain.inventory;

import jakarta.persistence.*;
import lombok.*;
import java.util.Locale;

@Entity @Table(name = "stock_operation_types") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockOperationType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "name_key", nullable = false, unique = true, length = 200) private String nameKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private StockOperation.Kind kind;
    @Column(nullable = false) private boolean active = true;
    @Version private long version;
    public StockOperationType(String name, StockOperation.Kind kind) { revise(name, kind, true); }
    public void revise(String name, StockOperation.Kind kind, boolean active) {
        this.name = name.strip(); this.nameKey = this.name.toLowerCase(Locale.ROOT); this.kind = kind; this.active = active;
    }
}
