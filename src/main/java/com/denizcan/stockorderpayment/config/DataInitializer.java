package com.denizcan.stockorderpayment.config;

import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.domain.user.User;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.denizcan.stockorderpayment.service.auth.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Profile("!prod")
    CommandLineRunner seedUsers() {
        return args -> {
            createIfMissing("admin", "admin123", Role.ADMIN);
            createIfMissing("user", "user123", Role.USER);
        };
    }

    @Bean
    @Profile("prod")
    CommandLineRunner bootstrapAdmin(AccountService accounts, Environment environment) {
        return args -> {
            String username = environment.getProperty("app.bootstrap.admin.username", "");
            String password = environment.getProperty("app.bootstrap.admin.password", "");
            if (!username.isBlank() || !password.isBlank()) {
                accounts.bootstrapAdmin(username, password);
            }
        };
    }

    private void createIfMissing(String username, String rawPassword, Role role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(new User(username, passwordEncoder.encode(rawPassword), role));
            log.info("Seeded user '{}' with role {}", username, role);
        }
    }
}
