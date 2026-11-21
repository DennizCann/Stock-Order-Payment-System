package com.denizcan.stockorderpayment.domain.inventory;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name = "stock_operations") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockOperation {
    public enum Kind { IN, OUT, TRANSFER }
    @Id private UUID id;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Column(nullable = false, length = 64) private String username;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Kind kind;
    @Column(name = "source_branch_id") private Long sourceBranchId;
    @Column(name = "target_branch_id") private Long targetBranchId;
    @Column(name = "source_name", length = 100) private String sourceName;
    @Column(name = "target_name", length = 100) private String targetName;
    @Column(name = "type_id") private Long typeId;
    @Column(length = 100) private String label;
    @Column(length = 255) private String note;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "operation_id", nullable = false)
    private List<StockOperationLine> lines = new ArrayList<>();

    public StockOperation(UUID id, String fingerprint, Long actorId, String username, Kind kind,
                          Long sourceBranchId, Long targetBranchId, String sourceName, String targetName,
                          Long typeId, String label, String note) {
        this.id = id; this.fingerprint = fingerprint; this.actorId = actorId; this.username = username; this.kind = kind;
        this.sourceBranchId = sourceBranchId; this.targetBranchId = targetBranchId; this.sourceName = sourceName;
        this.targetName = targetName; this.typeId = typeId; this.label = label; this.note = note; this.createdAt = Instant.now();
    }
}
