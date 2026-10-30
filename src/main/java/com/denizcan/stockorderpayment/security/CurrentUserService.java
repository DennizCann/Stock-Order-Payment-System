package com.denizcan.stockorderpayment.security;

import com.denizcan.stockorderpayment.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Long idForUsername(String username) {
        return userRepository.findByUsername(username)
                .filter(user -> user.isEnabled())
                .map(user -> user.getId())
                .orElseThrow(() -> new AccessDeniedException("Account is unavailable"));
    }
}
