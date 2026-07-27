package com.denizcan.stockorderpayment.config;

import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.domain.user.User;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedUsers() {
        return args -> {
            createIfMissing("admin", "admin123", Role.ADMIN);
            createIfMissing("user", "user123", Role.USER);
        };
    }

    private void createIfMissing(String username, String rawPassword, Role role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(new User(username, passwordEncoder.encode(rawPassword), role));
            log.info("Seeded user '{}' with role {}", username, role);
        }
    }
}
