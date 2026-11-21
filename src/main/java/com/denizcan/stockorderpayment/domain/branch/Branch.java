package com.denizcan.stockorderpayment.domain.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "branches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    public enum Kind { BRANCH, WAREHOUSE }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @org.hibernate.annotations.ColumnDefault("'BRANCH'")
    @Column(nullable = false, length = 16)
    private Kind kind = Kind.BRANCH;

    @org.hibernate.annotations.ColumnDefault("true")
    @Column(nullable = false)
    private boolean active = true;

    @jakarta.persistence.Version
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(nullable = false)
    private long version;

    public void revise(String name, Kind kind, boolean active) {
        this.name = name.strip(); this.kind = kind; this.active = active;
    }

    public Branch(String name) {
        this.name = name;
    }
}
