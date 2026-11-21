package com.denizcan.stockorderpayment.domain.order;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="stock_publications") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class StockPublication {
    public enum State { QUEUED, SENDING, SUBMITTED, SUCCESS, FAILED, UNCERTAIN }
    @Id private UUID id;
    @Column(nullable=false,length=64) private String connectionKey;
    @Column(nullable=false,length=64) private String fingerprint;
    @Column(unique=true,length=32) private String activeKey;
    @Column(nullable=false,length=64) private String username;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private State state;
    @Column(length=160) private String batchId;
    @Column(nullable=false,length=255) private String message;
    @Column(nullable=false) private Instant createdAt;
    private Instant lastCheckedAt;
    private Instant nextCheckAt;
    @OneToMany(cascade=CascadeType.ALL,orphanRemoval=true) @JoinColumn(name="publication_id",nullable=false)
    private List<StockPublicationLine> lines=new ArrayList<>();
    public StockPublication(UUID id,String connection,String fingerprint,String actor,List<StockPublicationLine> items) {
        this.id=id;connectionKey=connection;this.fingerprint=fingerprint;username=actor;activeKey="TRENDYOL";state=State.QUEUED;
        createdAt=Instant.now();message="Gönderim bekliyor.";lines.addAll(items);
    }
    public void sending() { state=State.SENDING;lastCheckedAt=Instant.now();message="Gönderiliyor; henüz onaylanmadı."; }
    public void submitted(String batch) {batchId=batch;state=State.SUBMITTED;nextCheckAt=Instant.now().plusSeconds(15);message="Kuyruğa alındı; ürün sonuçları bekleniyor.";}
    public void pending() { lastCheckedAt=Instant.now();nextCheckAt=lastCheckedAt.plusSeconds(30);message="Ürün sonuçları henüz tamamlanmadı; tekrar kontrol edilecek."; }
    public void finish(State state,String message) {this.state=state;this.message=message;lastCheckedAt=Instant.now();nextCheckAt=null;
        if(state==State.SUCCESS||state==State.FAILED) activeKey=null;
    }
}
