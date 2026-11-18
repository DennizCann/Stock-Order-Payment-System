package com.denizcan.stockorderpayment.service.auth;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.domain.user.EmployeeRole;
import com.denizcan.stockorderpayment.domain.user.Permission;
import java.util.Set;
import java.util.EnumSet;
import java.util.Collections;
import com.denizcan.stockorderpayment.repository.user.EmployeeRoleRepository;
import com.denizcan.stockorderpayment.domain.user.User;
import com.denizcan.stockorderpayment.repository.branch.BranchRepository;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AccountService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,64}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final BranchRepository branches;
    private final EmployeeRoleRepository employeeRoles;
    private final PasswordEncoder passwords;

    @Transactional
    public String invite(String username, Role requestedRole, Long branchId) {
        return invite(username, requestedRole, branchId, null, Set.of(), Set.of());
    }

    @Transactional
    public String invite(String username, Role requestedRole, Long branchId, Long employeeRoleId,
                         Set<Permission> excludedPermissions, Set<Permission> addedPermissions) {
        validateUsername(username);
        EmployeeRole employeeRole = employeeRoleId == null ? null : employeeRoles.findById(employeeRoleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seçilen rol bulunamadı."));
        if (employeeRole != null && requestedRole != null && employeeRole.getScope() != requestedRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol ile çalışma alanı uyuşmuyor.");
        }
        Role role = employeeRole != null ? employeeRole.getScope() : requestedRole == null ? Role.USER : requestedRole;
        Branch branch = branchForInvitation(role, branchId);
        Set<Permission> exclusions = excludedPermissions == null ? Set.of() : excludedPermissions;
        Set<Permission> basePermissions = employeeRole == null
                ? Permission.defaultsFor(role) : employeeRole.getPermissions();
        Set<Permission> additions = addedPermissions == null ? Set.of() : addedPermissions;
        validateExceptions(basePermissions, Permission.defaultsFor(role), exclusions, additions);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        User user = users.findByUsernameForUpdate(username)
                .orElseGet(() -> new User(username, passwords.encode(token), role));
        if (user.getId() != null && (user.isEnabled() || user.getActivationTokenHash() == null
                || user.getRole() == Role.ADMIN)) {
            throw accountExists();
        }
        user.prepareActivation(hash(token), Instant.now().plus(Duration.ofHours(24)), role, branch);
        user.setEmployeeRole(employeeRole);
        user.setExcludedPermissions(exclusions);
        user.setAddedPermissions(additions);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw accountExists();
        }
        return token;
    }

    @Transactional
    public void activate(String token, String password) {
        validatePassword(password);
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw invalidInvitation();
        }
        String tokenHash = hash(token);
        if (!users.existsByActivationTokenHashAndEnabledFalseAndActivationExpiresAtAfter(tokenHash, Instant.now())) {
            throw invalidInvitation();
        }
        String passwordHash = passwords.encode(password);
        if (users.activate(tokenHash, passwordHash, Instant.now()) != 1) {
            throw invalidInvitation();
        }
    }

    @Transactional
    public void bootstrapAdmin(String username, String password) {
        validateUsername(username);
        validatePassword(password);
        User existing = users.findByUsernameForUpdate(username).orElse(null);
        if (existing == null) {
            users.save(new User(username, passwords.encode(password), Role.ADMIN));
        } else if (existing.getRole() != Role.ADMIN || !existing.isEnabled()) {
            throw new IllegalStateException("Bootstrap username must identify an active administrator or be unused.");
        }
    }

    private void validateExceptions(Set<Permission> base, Set<Permission> available,
                                    Set<Permission> exclusions, Set<Permission> additions) {
        if (!base.containsAll(exclusions) || !available.containsAll(additions)
                || !Collections.disjoint(exclusions, additions)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Çalışma alanına uygun izinler ekle. Aynı yetkiyi hem ekleyip hem kaldıramazsın.");
        }
        EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
        effective.addAll(base);
        effective.addAll(additions);
        effective.removeAll(exclusions);
        if (effective.isEmpty()
                || effective.contains(Permission.BRANCH_SALE_CREATE) && !effective.contains(Permission.BRANCH_STOCK_VIEW)
                || effective.contains(Permission.ORDERS_CREATE) && !effective.contains(Permission.ORDERS_VIEW)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Çalışana en az bir yetki bırak. Satış için şube stoğu, sipariş oluşturmak için siparişleri görüntüleme yetkisi gerekiyor.");
        }
    }

    private Branch branchForInvitation(Role role, Long branchId) {
        if (role == Role.USER && branchId == null) {
            return null;
        }
        if (role != Role.BRANCH_EMPLOYEE || branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Merkez çalışanı veya şube çalışanı seç. Şube çalışanına bir şube ata.");
        }
        return branches.findById(branchId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Seçilen şube bulunamadı."));
    }

    private static void validateUsername(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kullanıcı adı 3–64 karakter olmalı. Harf, rakam, nokta, tire veya alt çizgi kullan.");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parola en az 12 karakter ve en fazla 72 bayt olmalı.");
        }
    }

    private static ResponseStatusException accountExists() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Bu kullanıcı adı zaten kullanımda.");
    }

    private static ResponseStatusException invalidInvitation() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bağlantı geçersiz veya süresi dolmuş.");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
