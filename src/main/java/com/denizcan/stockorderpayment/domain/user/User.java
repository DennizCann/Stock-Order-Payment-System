package com.denizcan.stockorderpayment.domain.user;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Setter
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_role_id")
    private EmployeeRole employeeRole;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "activation_token_hash", length = 64, unique = true)
    private String activationTokenHash;

    @Column(name = "activation_expires_at")
    private Instant activationExpiresAt;

    @ElementCollection
    @CollectionTable(name = "user_permission_exclusions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40)
    private Set<Permission> excludedPermissions = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "user_permission_additions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40)
    private Set<Permission> addedPermissions = new HashSet<>();

    public void setAddedPermissions(Set<Permission> permissions) {
        addedPermissions.clear();
        addedPermissions.addAll(permissions);
    }

    public void setExcludedPermissions(Set<Permission> permissions) {
        excludedPermissions.clear();
        excludedPermissions.addAll(permissions);
    }

    public void prepareActivation(String tokenHash, Instant expiresAt, Role role, Branch branch) {
        enabled = false;
        this.role = role;
        this.branch = branch;
        activationTokenHash = tokenHash;
        activationExpiresAt = expiresAt;
    }

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = true;
    }
}
