package com.denizcan.stockorderpayment.service.auth;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.domain.user.EmployeeRole;
import com.denizcan.stockorderpayment.domain.user.Permission;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
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
        return invite(username, requestedRole, branchId, employeeRoleId, excludedPermissions, addedPermissions, null);
    }

    @Transactional
    public String invite(String username, Role requestedRole, Long branchId, Long employeeRoleId,
                         Set<Permission> excludedPermissions, Set<Permission> addedPermissions, String fullName) {
        validateUsername(username);
        validateFullName(fullName);
        EmployeeRole employeeRole = employeeRoleId == null ? null : employeeRoles.findById(employeeRoleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seçilen rol bulunamadı."));
        if (employeeRole != null && requestedRole != null && employeeRole.getScope() != requestedRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol ile çalışma alanı uyuşmuyor.");
        }
        Role role = employeeRole != null ? employeeRole.getScope() : requestedRole == null ? Role.USER : requestedRole;
        Set<Permission> exclusions = excludedPermissions == null ? Set.of() : excludedPermissions;
        Set<Permission> basePermissions = employeeRole == null
                ? Permission.defaultsFor(role) : employeeRole.getPermissions();
        Set<Permission> additions = addedPermissions == null ? Set.of() : addedPermissions;
        validateExceptions(basePermissions, Permission.availableFor(role), exclusions, additions);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        User user = users.findByUsernameForUpdate(username)
                .orElseGet(() -> new User(username, passwords.encode(token), role));
        if (user.getId() != null && (user.isEnabled() || user.getActivationTokenHash() == null
                || user.getRole() == Role.ADMIN)) {
            throw accountExists();
        }
        Branch branch = branchForInvitation(role, branchId);
        user.prepareActivation(hash(token), Instant.now().plus(Duration.ofHours(24)), role, branch);
        user.setEmployeeRole(employeeRole);
        user.setFullName(fullName);
        user.setExcludedPermissions(exclusions);
        user.setAddedPermissions(additions);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw accountExists();
        }
        return token;
    }

    @Transactional(readOnly = true)
    public List<EmployeeAccount> employees() {
        return users.findByRoleNotOrderByUsernameAsc(Role.ADMIN).stream().map(EmployeeAccount::from).toList();
    }

    @Transactional
    public EmployeeAccount updateAccount(Long id, String revision, String username, String fullName,
                                         Role requestedRole, Long branchId, Long employeeRoleId, boolean enabled) {
        validateUsername(username);
        validateFullName(fullName);
        User user = users.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Çalışan bulunamadı."));
        if (user.getRole() == Role.ADMIN || requestedRole == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu ekrandan yönetici hesabı değiştirilemez veya oluşturulamaz.");
        }
        if (!EmployeeAccount.from(user).revision().equals(revision)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Çalışan bilgileri değişmiş. Listeyi yenileyip tekrar düzenle.");
        if (users.existsByUsernameAndIdNot(username, id)) throw accountExists();
        if (enabled && user.getActivationTokenHash() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Çalışanın önce parola bağlantısıyla hesabını etkinleştirmesi gerekiyor.");
        EmployeeRole employeeRole = employeeRoleId == null ? null : employeeRoles.findById(employeeRoleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seçilen rol bulunamadı."));
        Role scope = employeeRole == null ? requestedRole : employeeRole.getScope();
        if (scope == null || scope == Role.ADMIN || employeeRole != null && requestedRole != null && scope != requestedRole)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol ile çalışma alanı uyuşmuyor.");
        Branch branch = branchForInvitation(scope, branchId);
        Set<Permission> available = Permission.availableFor(scope);
        Set<Permission> additions = new HashSet<>(user.getAddedPermissions()); additions.retainAll(available);
        Set<Permission> exclusions = new HashSet<>(user.getExcludedPermissions()); exclusions.retainAll(available);
        Set<Permission> effective = new HashSet<>(employeeRole == null ? Permission.defaultsFor(scope) : employeeRole.getPermissions());
        effective.addAll(additions); effective.removeAll(exclusions);
        validateEffectivePermissions(effective);
        user.reviseAccount(username, fullName, scope, branch, employeeRole, enabled);
        user.setAddedPermissions(additions); user.setExcludedPermissions(exclusions);
        try { users.flush(); } catch (DataIntegrityViolationException exception) { throw accountExists(); }
        return EmployeeAccount.from(user);
    }

    @Transactional
    public EmployeeAccount updatePermissions(Long id, String revision, Set<Permission> selected) {
        User user = users.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Çalışan bulunamadı."));
        if (user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Yönetici hesabının izinleri bu ekrandan değiştirilemez.");
        }
        EmployeeAccount current = EmployeeAccount.from(user);
        if (!current.revision().equals(revision)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Çalışanın veya rolünün yetkileri değişmiş. Listeyi yenileyip tekrar düzenle.");
        }
        Set<Permission> available = Permission.availableFor(user.getRole());
        if (!available.containsAll(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Çalışma alanına uygun yetkiler seç.");
        }
        validateEffectivePermissions(selected);

        // Preserve existing personal exceptions even if the shared role has since changed.
        Set<Permission> additions = new HashSet<>(user.getAddedPermissions());
        Set<Permission> exclusions = new HashSet<>(user.getExcludedPermissions());
        for (Permission permission : available) {
            if (selected.contains(permission)) {
                exclusions.remove(permission);
                if (!current.basePermissions().contains(permission)) additions.add(permission);
            } else {
                additions.remove(permission);
                if (current.basePermissions().contains(permission)) exclusions.add(permission);
            }
        }
        user.setAddedPermissions(additions);
        user.setExcludedPermissions(exclusions);
        return EmployeeAccount.from(user);
    }

    public record EmployeeAccount(Long id, String username, String fullName, String roleName, Long employeeRoleId,
                                  Role scope, String branchName, Long branchId, String status,
                                  boolean enabled, Set<Permission> basePermissions, Set<Permission> permissions,
                                  String revision) {
        static EmployeeAccount from(User user) {
            EmployeeRole role = user.getEmployeeRole();
            Set<Permission> base = Set.copyOf(role == null
                    ? Permission.defaultsFor(user.getRole()) : role.getPermissions());
            String roleName = role == null
                    ? user.getRole() == Role.BRANCH_EMPLOYEE ? "Şube çalışanı" : "Merkez çalışanı"
                    : role.getName();
            String revision = hash(user.getId() + "|" + user.getUsername() + "|" + user.getFullName() + "|"
                    + user.getSessionVersion() + "|" + (user.getBranch() == null ? "" : user.getBranch().getId()) + "|" + user.getRole() + "|"
                    + (role == null ? "" : role.getId()) + "|" + user.isEnabled() + "|"
                    + base.stream().sorted().toList() + "|"
                    + user.getAddedPermissions().stream().sorted().toList() + "|"
                    + user.getExcludedPermissions().stream().sorted().toList());
            return new EmployeeAccount(user.getId(), user.getUsername(), user.getFullName(), roleName,
                    role == null ? null : role.getId(), user.getRole(),
                    user.getBranch() == null ? null : user.getBranch().getName(), user.getBranch() == null ? null : user.getBranch().getId(),
                    user.isEnabled() ? "ACTIVE" : user.getActivationTokenHash() != null ? "PENDING" : "DISABLED", user.isEnabled(),
                    base, Set.copyOf(Permission.forUser(user)), revision);
        }
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
        validateEffectivePermissions(effective);
    }

    private void validateEffectivePermissions(Set<Permission> effective) {
        if (effective.isEmpty() || !Permission.validDependencies(effective)
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
        return branches.findForUpdate(branchId).filter(Branch::isActive).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Seçilen şube bulunamadı."));
    }

    static void validateUsername(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kullanıcı adı 3–64 karakter olmalı. Harf, rakam, nokta, tire veya alt çizgi kullan.");
        }
    }

    static void validateFullName(String fullName) {
        if (fullName != null && fullName.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Çalışan adı en fazla 100 karakter olabilir.");
    }

    static void validatePassword(String password) {
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

    public record PasswordLink(String token,EmployeeAccount account) {}
    @Transactional
    public PasswordLink passwordLink(Long id,String revision) {
        User user=users.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Çalışan bulunamadı."));
        if(user.getRole()==Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Yönetici kendi parolasını Hesabım bölümünden değiştirmeli.");
        if(!EmployeeAccount.from(user).revision().equals(revision)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Çalışan bilgileri değişmiş. Listeyi yenile.");
        if(!user.isEnabled()&&user.getActivationTokenHash()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Kapalı hesabı önce etkinleştir; sonra parola bağlantısı oluştur.");
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.prepareActivation(hash(token),Instant.now().plus(Duration.ofHours(24)),user.getRole(),user.getBranch());
        users.flush();return new PasswordLink(token,EmployeeAccount.from(user));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
