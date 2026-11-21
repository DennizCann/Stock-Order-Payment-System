package com.denizcan.stockorderpayment.web.auth;

import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.domain.user.Permission;
import java.util.Set;
import com.denizcan.stockorderpayment.service.auth.AccountService;
import com.denizcan.stockorderpayment.service.auth.AuthService;
import com.denizcan.stockorderpayment.web.auth.dto.AuthResponse;
import com.denizcan.stockorderpayment.web.auth.dto.LoginRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.denizcan.stockorderpayment.service.auth.AccountService.EmployeeAccount;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountService accounts;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Invitation> invite(@RequestBody InviteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Invitation(accounts.invite(request.username(), request.role(), request.branchId(), request.employeeRoleId(), request.excludedPermissions(), request.addedPermissions(), request.fullName())));
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EmployeeAccount>> employees() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.employees());
    }

    @PutMapping("/accounts/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeAccount> updatePermissions(
            @PathVariable Long id, @Valid @RequestBody UpdatePermissionsRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.updatePermissions(id, request.revision(), request.permissions()));
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeAccount> updateAccount(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.updateAccount(id, request.revision(),
                request.username(), request.fullName(), request.role(), request.branchId(), request.employeeRoleId(), request.enabled()));
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@RequestBody ActivateRequest request) {
        accounts.activate(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    public record PasswordLinkRequest(@NotBlank String revision) {}
    @PostMapping("/accounts/{id}/password-link") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountService.PasswordLink> passwordLink(@PathVariable Long id,@Valid @RequestBody PasswordLinkRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.passwordLink(id,request.revision()));
    }

    public record InviteRequest(String username, Role role, Long branchId, Long employeeRoleId,
                                Set<Permission> excludedPermissions, Set<Permission> addedPermissions, String fullName) {}
    public record UpdateAccountRequest(@NotBlank String revision, @NotBlank String username,
                                       @jakarta.validation.constraints.Size(max = 100) String fullName, Role role,
                                       Long branchId, Long employeeRoleId, @NotNull Boolean enabled) {}
    public record UpdatePermissionsRequest(@NotBlank String revision,
                                           @NotEmpty Set<@NotNull Permission> permissions) {}
    public record Invitation(String token) {}
    public record ActivateRequest(String token, String password) {}
}
