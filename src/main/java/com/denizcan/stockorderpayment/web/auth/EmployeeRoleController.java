package com.denizcan.stockorderpayment.web.auth;

import com.denizcan.stockorderpayment.domain.user.EmployeeRole;
import com.denizcan.stockorderpayment.domain.user.Permission;
import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.repository.user.EmployeeRoleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmployeeRoleController {
    private final EmployeeRoleRepository roles;
    private final com.denizcan.stockorderpayment.repository.user.UserRepository users;

    @GetMapping
    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findAllByOrderByNameAsc().stream().map(RoleResponse::from).toList();
    }

    @GetMapping("/permissions")
    public List<PermissionOption> permissions() {
        return Arrays.stream(Permission.values())
                .map(permission -> new PermissionOption(permission, permission.label(), permission.scope(),
                        Permission.defaultsFor(permission.scope()).contains(permission)))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        validatePermissions(request);
        return save(new EmployeeRole(request.name(), request.scope(), request.permissions()));
    }

    @PutMapping("/{id}")
    @Transactional
    public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        validatePermissions(request);
        EmployeeRole role = roles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol bulunamadı."));
        if (role.getScope() != request.scope()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mevcut rolün çalışma alanı değiştirilemez. Diğer alan için yeni rol oluştur.");
        }
        role.revise(request.name(), request.permissions());
        return save(role);
    }

    private RoleResponse save(EmployeeRole role) {
        try {
            return RoleResponse.from(roles.saveAndFlush(role));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu rol adı zaten kullanımda.");
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        EmployeeRole role = roles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol bulunamadı."));
        if (users.existsByEmployeeRoleId(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Bu rol çalışanlara atanmış. Önce çalışanları başka bir role geçir.");
        try { roles.delete(role); roles.flush(); }
        catch (DataIntegrityViolationException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Rol kullanımda. Listeyi yenile."); }
    }

    private void validatePermissions(RoleRequest request) {
        if (request.scope() == Role.ADMIN
                || !Permission.availableFor(request.scope()).containsAll(request.permissions())
                || !Permission.validDependencies(request.permissions())
                || request.permissions().contains(Permission.BRANCH_SALE_CREATE)
                    && !request.permissions().contains(Permission.BRANCH_STOCK_VIEW)
                || request.permissions().contains(Permission.ORDERS_CREATE)
                    && !request.permissions().contains(Permission.ORDERS_VIEW)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Çalışma alanına uygun yetkiler seç. Satış için şube stoğunu, sipariş oluşturmak için siparişleri görüntüleme yetkisini de ekle.");
        }
    }

    public record RoleRequest(@NotBlank @Size(max = 64) String name, @NotNull Role scope,
                              @NotEmpty Set<@NotNull Permission> permissions) {}

    public record RoleResponse(Long id, String name, Role scope, Set<Permission> permissions) {
        static RoleResponse from(EmployeeRole role) {
            return new RoleResponse(role.getId(), role.getName(), role.getScope(), Set.copyOf(role.getPermissions()));
        }
    }

    public record PermissionOption(Permission code, String label, Role scope, boolean defaultGranted) {}
}
