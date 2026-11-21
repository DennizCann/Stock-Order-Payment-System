package com.denizcan.stockorderpayment.domain.order;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="channel_stock_policies", uniqueConstraints={
    @UniqueConstraint(columnNames={"source","product_id"}), @UniqueConstraint(columnNames={"source","external_code"})})
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ChannelStockPolicy {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private OrderSource source;
    @Column(name="product_id",nullable=false) private Long productId;
    @Column(name="external_code",nullable=false,length=100) private String externalCode;
    @Column(nullable=false) private int maximumQuantity;
    @Column(nullable=false) private int safetyStock;
    @Column(nullable=false) private boolean enabled;
    @Version private long version;
    public ChannelStockPolicy(OrderSource source, Long productId) { this.source=source;this.productId=productId; }
    public void revise(String code,int maximum,int safety,boolean enabled) {
        this.externalCode=code.strip();this.maximumQuantity=maximum;this.safetyStock=safety;this.enabled=enabled;
    }
    public int publishable(int available, boolean active) {
        return enabled && active ? Math.min(maximumQuantity,Math.max(0,available-safetyStock)) : 0;
    }
}
