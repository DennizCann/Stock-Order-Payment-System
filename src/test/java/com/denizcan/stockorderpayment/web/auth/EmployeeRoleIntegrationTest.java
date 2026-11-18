package com.denizcan.stockorderpayment.web.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeRoleIntegrationTest {
    private static final String PASSWORD = "employee-role-test-password";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void centralPermissionsRestrictEndpointsAndChangingTheRoleUpdatesExistingTokens() throws Exception {
        String admin = login("admin", "admin123").get("accessToken").asText();
        long roleId = createRole(admin, "Depo asistanı", "USER", Set.of("STOCK_VIEW"));
        String employee = inviteAndActivate(admin, "role-stock-reader", roleId, null);

        mvc.perform(get("/api/v1/products").header("Authorization", bearer(employee)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders").header("Authorization", bearer(employee)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles").header("Authorization", bearer(employee)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Forbidden", "sku", "FORBIDDEN", "price", 1))))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/roles/" + roleId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("Sipariş asistanı", "USER", Set.of("ORDERS_VIEW"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders").header("Authorization", bearer(employee)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products").header("Authorization", bearer(employee)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products/999999/stock").header("Authorization", bearer(employee)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "role-stock-reader", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("Sipariş asistanı"))
                .andExpect(jsonPath("$.permissions").value(hasItem("ORDERS_VIEW")))
                .andExpect(jsonPath("$.permissions").value(not(hasItem("STOCK_VIEW"))));
    }

    @Test
    void branchRoleCanViewItsStockButNeedsSalePermissionAndRemainsScopedToItsBranch() throws Exception {
        String admin = login("admin", "admin123").get("accessToken").asText();
        long roleId = createRole(admin, "Şube asistanı", "BRANCH_EMPLOYEE", Set.of("BRANCH_STOCK_VIEW"));
        String branch = mvc.perform(post("/api/v1/branches").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Yetki test şubesi\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long branchId = json.readTree(branch).get("id").asLong();
        String employee = inviteAndActivate(admin, "role-branch-reader", roleId, branchId);
        String sale = json.writeValueAsString(Map.of("productId", Long.MAX_VALUE, "quantity", 1, "saleId", UUID.randomUUID()));
        mvc.perform(get("/api/v1/branches/me/stock").header("Authorization", bearer(employee)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/branches/me/sales").header("Authorization", bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/branches/" + branchId + "/stock").header("Authorization", bearer(employee)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/roles/" + roleId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("Satış danışmanı", "BRANCH_EMPLOYEE", Set.of("BRANCH_STOCK_VIEW", "BRANCH_SALE_CREATE"))))
                .andExpect(status().isOk());
        // The same token now reaches the sale service, which rejects the product absent from this branch.
        mvc.perform(post("/api/v1/branches/me/sales").header("Authorization", bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products").header("Authorization", bearer(employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesRoleScopeDependenciesAndAdministratorOnlyManagement() throws Exception {
        String admin = login("admin", "admin123").get("accessToken").asText();
        String user = login("user", "user123").get("accessToken").asText();
        mvc.perform(post("/api/v1/roles").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(roleBody("Denied", "USER", Set.of("PRODUCTS_VIEW"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles/permissions").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        for (String invalid : Set.of(
                roleBody("Admin", "ADMIN", Set.of("PRODUCTS_VIEW")),
                roleBody("Wrong scope", "USER", Set.of("BRANCH_STOCK_VIEW")),
                roleBody("Sale without stock", "BRANCH_EMPLOYEE", Set.of("BRANCH_SALE_CREATE")),
                roleBody("Order without view", "USER", Set.of("ORDERS_CREATE")),
                roleBody("Empty", "USER", Set.of()))) {
            mvc.perform(post("/api/v1/roles").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        long roleId = createRole(admin, "Katalog sorumlusu", "USER", Set.of("PRODUCTS_VIEW"));
        mvc.perform(put("/api/v1/roles/" + roleId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("Şubeye dönüşüm", "BRANCH_EMPLOYEE", Set.of("BRANCH_STOCK_VIEW"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/accounts").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "role-invalid", "employeeRoleId", roleId,
                                "role", "ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/accounts").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "role-missing", "employeeRoleId", Long.MAX_VALUE))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/roles").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody(" katalog sorumlusu ", "USER", Set.of("PRODUCTS_VIEW"))))
                .andExpect(status().isConflict());
    }

    @Test
    void personalExceptionsAddAndRemovePermissionsWithoutChangingOtherEmployees() throws Exception {
        String admin = login("admin", "admin123").get("accessToken").asText();
        long roleId = createRole(admin, "Satış destek", "USER", Set.of("PRODUCTS_VIEW", "ORDERS_VIEW"));
        String standard = inviteAndActivate(admin, "personal-standard", roleId, null);
        String exception = inviteAndActivate(admin, "personal-exception", roleId, null, Map.of(
                "excludedPermissions", Set.of("ORDERS_VIEW"), "addedPermissions", Set.of("STOCK_VIEW")));

        mvc.perform(get("/api/v1/orders").header("Authorization", bearer(standard)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/999999/stock").header("Authorization", bearer(standard)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders").header("Authorization", bearer(exception)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products/999999/stock").header("Authorization", bearer(exception)))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/roles/" + roleId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("Satış destek", "USER", Set.of("PRODUCTS_VIEW", "ORDERS_VIEW", "ORDERS_CREATE"))))
                .andExpect(status().isOk());
        // Removing order visibility for one person also blocks dependent order creation after a role update.
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer(exception))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer(standard))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        for (Map<String, Object> invalid : java.util.List.<Map<String, Object>>of(
                Map.of("addedPermissions", Set.of("BRANCH_SALE_CREATE")),
                Map.of("addedPermissions", Set.of("ORDERS_VIEW"), "excludedPermissions", Set.of("ORDERS_VIEW")),
                Map.of("excludedPermissions", Set.of("PRODUCTS_VIEW", "ORDERS_VIEW", "ORDERS_CREATE")))) {
            var request = new java.util.HashMap<String, Object>(invalid);
            request.put("username", "personal-invalid");
            request.put("employeeRoleId", roleId);
            mvc.perform(post("/api/v1/auth/accounts").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
        JsonNode profile = login("personal-exception", PASSWORD);
        org.assertj.core.api.Assertions.assertThat(profile.get("roleName").asText()).isEqualTo("Satış destek");
        org.assertj.core.api.Assertions.assertThat(profile.get("permissions").toString())
                .contains("STOCK_VIEW").doesNotContain("ORDERS_VIEW", "ORDERS_CREATE");
    }

    private long createRole(String admin, String name, String scope, Set<String> permissions) throws Exception {
        String result = mvc.perform(post("/api/v1/roles").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(roleBody(name, scope, permissions)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }

    private String inviteAndActivate(String admin, String username, long roleId, Long branchId) throws Exception {
        return inviteAndActivate(admin, username, roleId, branchId, Map.of());
    }

    private String inviteAndActivate(String admin, String username, long roleId, Long branchId,
                                     Map<String, Object> exceptions) throws Exception {
        var request = new java.util.HashMap<String, Object>(exceptions);
        request.put("username", username);
        request.put("employeeRoleId", roleId);
        if (branchId != null) request.put("branchId", branchId);
        String invitation = mvc.perform(post("/api/v1/auth/accounts").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", json.readTree(invitation).get("token").asText(),
                                "password", PASSWORD))))
                .andExpect(status().isNoContent());
        return login(username, PASSWORD).get("accessToken").asText();
    }

    private JsonNode login(String username, String password) throws Exception {
        String result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result);
    }

    private String roleBody(String name, String scope, Set<String> permissions) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "scope", scope, "permissions", permissions));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
