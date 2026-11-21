package com.denizcan.stockorderpayment.web.auth;
import com.denizcan.stockorderpayment.service.auth.SelfAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/auth/me") @RequiredArgsConstructor
public class SelfAccountController {
    private final SelfAccountService accounts;
    public record Update(@NotBlank String revision,@NotBlank @Size(max=64) String username,@Size(max=100) String fullName,@Size(max=200) String currentPassword) {}
    public record Password(@NotBlank @Size(max=200) String currentPassword,@NotBlank @Size(max=72) String newPassword) {}
    @GetMapping public ResponseEntity<SelfAccountService.Profile> current(Principal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.current(actor.getName()));
    }
    @PutMapping public ResponseEntity<SelfAccountService.Profile> update(@Valid @RequestBody Update request,Principal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.update(actor.getName(),request.revision(),request.username(),request.fullName(),request.currentPassword()));
    }
    @PostMapping("/password") public ResponseEntity<Void> password(@Valid @RequestBody Password request,Principal actor) {
        accounts.changePassword(actor.getName(),request.currentPassword(),request.newPassword());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
