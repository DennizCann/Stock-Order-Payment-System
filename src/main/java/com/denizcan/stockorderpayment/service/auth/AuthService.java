package com.denizcan.stockorderpayment.service.auth;

import com.denizcan.stockorderpayment.domain.user.User;
import com.denizcan.stockorderpayment.domain.user.Permission;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.denizcan.stockorderpayment.security.JwtService;
import com.denizcan.stockorderpayment.web.auth.dto.AuthResponse;
import com.denizcan.stockorderpayment.web.auth.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name(), user.getSessionVersion());
        String branchName = user.getBranch() == null ? null : user.getBranch().getName();
        String roleName = user.getEmployeeRole() == null ? user.getRole().name() : user.getEmployeeRole().getName();
        return AuthResponse.bearer(token, user.getUsername(), user.getRole().name(), branchName,
                user.getBranch()==null?null:user.getBranch().getId(),roleName, Permission.forUser(user));
    }
}
