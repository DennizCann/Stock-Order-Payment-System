package com.denizcan.stockorderpayment.repository.user;

import com.denizcan.stockorderpayment.domain.user.User;
import com.denizcan.stockorderpayment.domain.user.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = {"branch", "employeeRole"})
    List<User> findByRoleNotOrderByUsernameAsc(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUsername(String username);

    boolean existsByActivationTokenHashAndEnabledFalseAndActivationExpiresAtAfter(String tokenHash, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Modifying
    @Query("""
            update User u set u.password = :passwordHash, u.enabled = true,
                u.activationTokenHash = null, u.activationExpiresAt = null
            where u.enabled = false and u.activationTokenHash = :tokenHash
                and u.activationExpiresAt > :now
            """)
    int activate(@Param("tokenHash") String tokenHash, @Param("passwordHash") String passwordHash,
                 @Param("now") Instant now);
}
