package com.denizcan.stockorderpayment.web.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        String username,
        String role
) {

    public static AuthResponse bearer(String token, String username, String role) {
        return new AuthResponse(token, "Bearer", username, role);
    }
}
