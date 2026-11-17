package com.denizcan.stockorderpayment.web.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AuthResponse(
        String accessToken,
        String tokenType,
        String username,
        String role,
        @JsonInclude(JsonInclude.Include.NON_NULL) String branchName
) {

    public static AuthResponse bearer(String token, String username, String role, String branchName) {
        return new AuthResponse(token, "Bearer", username, role, branchName);
    }
}
