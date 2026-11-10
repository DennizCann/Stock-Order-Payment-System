package com.denizcan.stockorderpayment.web.auth;

import com.denizcan.stockorderpayment.service.auth.AccountService;
import com.denizcan.stockorderpayment.service.auth.AuthService;
import com.denizcan.stockorderpayment.web.auth.dto.AuthResponse;
import com.denizcan.stockorderpayment.web.auth.dto.LoginRequest;
import jakarta.validation.Valid;
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
                .body(new Invitation(accounts.invite(request.username())));
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@RequestBody ActivateRequest request) {
        accounts.activate(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    public record InviteRequest(String username) {}
    public record Invitation(String token) {}
    public record ActivateRequest(String token, String password) {}
}
