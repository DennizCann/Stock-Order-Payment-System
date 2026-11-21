package com.denizcan.stockorderpayment.web.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.denizcan.stockorderpayment.domain.user.Permission;
import java.util.Set;

public record AuthResponse(
        String accessToken,
        String tokenType,
        String username,
        String role,
        @JsonInclude(JsonInclude.Include.NON_NULL) String branchName,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long branchId,
        String roleName,
        Set<Permission> permissions
) {

    public static AuthResponse bearer(String token, String username, String role, String branchName,
                                      Long branchId,String roleName, Set<Permission> permissions) {
        return new AuthResponse(token, "Bearer", username, role, branchName,branchId, roleName, permissions);
    }
}
