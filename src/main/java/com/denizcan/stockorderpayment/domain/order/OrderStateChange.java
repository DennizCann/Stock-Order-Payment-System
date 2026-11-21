package com.denizcan.stockorderpayment.domain.order;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="order_state_changes") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class OrderStateChange {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long orderId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private OrderStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private OrderStatus newStatus;
    @Column(nullable=false,length=64) private String username;
    @Column(length=255) private String note;
    @Column(nullable=false) private Instant createdAt;
    public OrderStateChange(Long orderId,OrderStatus previous,OrderStatus next,String actor,String note) {
        this.orderId=orderId;previousStatus=previous;newStatus=next;username=actor;this.note=note;createdAt=Instant.now();
    }
}
