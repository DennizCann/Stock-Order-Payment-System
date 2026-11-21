package com.denizcan.stockorderpayment.domain.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "employee_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "name_key", nullable = false, unique = true, length = 128)
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private Role scope;

    @ElementCollection
    @CollectionTable(name = "employee_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40)
    private Set<Permission> permissions = new HashSet<>();

    public EmployeeRole(String name, Role scope, Set<Permission> permissions) {
        this.scope = scope;
        revise(name, permissions);
    }

    public void revise(String name, Set<Permission> permissions) {
        this.name = name.strip();
        this.nameKey = this.name.toLowerCase(Locale.ROOT);
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }
}
