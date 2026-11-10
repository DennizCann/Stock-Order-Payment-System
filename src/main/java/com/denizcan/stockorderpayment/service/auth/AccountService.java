package com.denizcan.stockorderpayment.service.auth;

import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.domain.user.User;
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
    private final PasswordEncoder passwords;

    @Transactional
    public String invite(String username) {
        validateUsername(username);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        User user = users.findByUsernameForUpdate(username)
                .orElseGet(() -> new User(username, passwords.encode(token), Role.USER));
        if (user.getId() != null && (user.isEnabled() || user.getActivationTokenHash() == null
                || user.getRole() != Role.USER)) {
            throw accountExists();
        }
        user.prepareActivation(hash(token), Instant.now().plus(Duration.ofHours(24)));
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
