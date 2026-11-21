package com.denizcan.stockorderpayment.web.auth;

import com.denizcan.stockorderpayment.domain.user.*;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ManagementLifecycleIntegrationTest {
    private static final String PASSWORD = "ManagementTest12345";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    @Test
    void renamingAndReusingUsernameNeverGivesAnOldTokenAccessToAnotherAccount() throws Exception {
        String admin = login("admin", "admin123"), username = unique("worker");
        JsonNode first = invite(admin, username, null, null, true);
        String oldToken = login(username, PASSWORD), renamed = unique("renamed");
        Map<String,Object> update = profile(first); update.put("username", renamed); update.put("fullName", "Ayşe Çelik");
        JsonNode saved = update(admin, first.path("id").asLong(), update, 200);
        assertThat(saved.path("fullName").asText()).isEqualTo("Ayşe Çelik");
        deniedToken(oldToken);
        invite(admin, username, null, null, true);
        deniedToken(oldToken);
        String newToken = login(renamed, PASSWORD);
        mvc.perform(get("/api/v1/products").header("Authorization", newToken)).andExpect(status().isOk());
        update = profile(saved); update.put("enabled", false);
        JsonNode disabled = update(admin, saved.path("id").asLong(), update, 200);
        assertThat(disabled.path("status").asText()).isEqualTo("DISABLED"); deniedToken(newToken);
        update = profile(disabled); update.put("enabled", true); update(admin, disabled.path("id").asLong(), update, 200);
        deniedToken(newToken); login(renamed, PASSWORD);
    }

    @Test
    void profileEditingHandlesRoleBranchStaleEditsAndProtectsAdministrator() throws Exception {
        String admin = login("admin", "admin123");
        JsonNode person = invite(admin, unique("worker"), null, null, true);
        JsonNode branch = createBranch(admin, "BRANCH");
        Map<String,Object> request = profile(person); request.put("role", "BRANCH_EMPLOYEE"); request.put("branchId", branch.path("id").asLong());
        JsonNode moved = update(admin, person.path("id").asLong(), request, 200);
        assertThat(moved.path("branchId").asLong()).isEqualTo(branch.path("id").asLong());
        update(admin, person.path("id").asLong(), request, 409);
        String employee = login(person.path("username").asText(), PASSWORD);
        mvc.perform(get("/api/v1/branches/me/stock").header("Authorization", employee)).andExpect(status().isOk());
        request = profile(moved); request.put("role", "ADMIN"); update(admin, moved.path("id").asLong(), request, 403);
        long adminId = users.findByUsername("admin").orElseThrow().getId();
        request.put("role", "USER"); update(admin, adminId, request, 403);
        JsonNode pending = invite(admin, unique("pending"), null, null, false);
        request = profile(pending); request.put("enabled", true); update(admin, pending.path("id").asLong(), request, 400);
        request = profile(pending); request.put("username", person.path("username").asText());
        update(admin, pending.path("id").asLong(), request, 409);
        update(employee, person.path("id").asLong(), profile(moved), 403);
    }

    @Test
    void delegatedLocationManagerCanEditArchiveAndRestoreButCannotManageEmployees() throws Exception {
        String admin = login("admin", "admin123");
        long role = role(admin, "USER", List.of("BRANCHES_VIEW", "BRANCHES_MANAGE"));
        JsonNode employee = invite(admin, unique("manager"), role, null, true);
        String token = login(employee.path("username").asText(), PASSWORD);
        JsonNode branch = createBranch(token, "WAREHOUSE"); long id = branch.path("id").asLong();
        JsonNode changed = branchUpdate(token, branch, "Renamed " + UUID.randomUUID(), true, 200);
        branchUpdate(token, branch, "Stale", true, 409);
        JsonNode archived = branchUpdate(token, changed, changed.path("name").asText(), false, 200);
        JsonNode locations = read(token, "/api/v1/branches");
        assertThat(locations).noneSatisfy(b -> assertThat(b.path("id").asLong()).isEqualTo(id));
        JsonNode restored = branchUpdate(token, archived, changed.path("name").asText(), true, 200);
        assertThat(restored.path("kind").asText()).isEqualTo("WAREHOUSE");
        mvc.perform(get("/api/v1/auth/accounts").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/accounts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"unauthorized\"}")).andExpect(status().isForbidden());
        String ordinary = login("user", "user123");
        mvc.perform(get("/api/v1/branches").header("Authorization", ordinary)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/branches").header("Authorization", ordinary).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
    }

    @Test
    void locationArchiveRequiresEmptyStockAndNoAssignedAccountsAndRejectsNewOperations() throws Exception {
        String admin = login("admin", "admin123"); JsonNode branch = createBranch(admin, "BRANCH");
        JsonNode employee = invite(admin, unique("branchuser"), null, branch.path("id").asLong(), true);
        branchUpdate(admin, branch, branch.path("name").asText(), false, 409);
        Map<String,Object> moved = profile(employee); moved.put("role", "USER"); moved.put("branchId", null);
        update(admin, employee.path("id").asLong(), moved, 200);
        JsonNode product = result(mvc.perform(post("/api/v1/products").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Item", "sku", unique("sku"), "price", 1, "quantity", 0))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String path = "/api/v1/branches/" + branch.path("id").asLong() + "/stock/" + product.path("id").asLong();
        mvc.perform(put(path).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()); branchUpdate(admin, branch, branch.path("name").asText(), false, 409);
        mvc.perform(put(path).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0,\"version\":0}"))
                .andExpect(status().isOk());
        branchUpdate(admin, branch, branch.path("name").asText(), false, 200);
        mvc.perform(put(path).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/stock-movements").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("productId", product.path("id").asLong(), "branchId", branch.path("id").asLong(),
                        "direction", "IN", "quantity", 1)))).andExpect(status().isConflict());
    }

    @Test
    void unusedRolesCanBeRemovedButAssignedRolesMustBeReassignedFirst() throws Exception {
        String admin = login("admin", "admin123"); long role = role(admin, "USER", List.of("PRODUCTS_VIEW"));
        JsonNode employee = invite(admin, unique("reader"), role, null, true);
        mvc.perform(delete("/api/v1/roles/" + role).header("Authorization", admin)).andExpect(status().isConflict());
        Map<String,Object> change = profile(employee); change.put("employeeRoleId", null); change.put("role", "USER");
        update(admin, employee.path("id").asLong(), change, 200);
        mvc.perform(delete("/api/v1/roles/" + role).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    private String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
    private String login(String username, String password) throws Exception {
        JsonNode response = result(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + response.path("accessToken").asText();
    }
    private void deniedToken(String token) throws Exception {
        mvc.perform(get("/api/v1/products").header("Authorization", token)).andExpect(status().is4xxClientError());
    }
    private JsonNode result(String value) throws Exception { return json.readTree(value); }
    private JsonNode read(String token, String path) throws Exception {
        return result(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
    private JsonNode invite(String admin, String name, Long role, Long branch, boolean activate) throws Exception {
        Map<String,Object> body = new HashMap<>(); body.put("username", name); body.put("role", branch == null ? "USER" : "BRANCH_EMPLOYEE");
        body.put("employeeRoleId", role); body.put("branchId", branch);
        JsonNode invitation = result(mvc.perform(post("/api/v1/auth/accounts").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        if (activate) mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", invitation.path("token").asText(), "password", PASSWORD))))
                .andExpect(status().isNoContent());
        for (JsonNode person : read(admin, "/api/v1/auth/accounts")) if (person.path("username").asText().equals(name)) return person;
        throw new AssertionError("Account missing");
    }
    private Map<String,Object> profile(JsonNode person) {
        Map<String,Object> body = new HashMap<>(); body.put("revision", person.path("revision").asText()); body.put("username", person.path("username").asText());
        body.put("role", person.path("scope").asText()); body.put("branchId", person.path("branchId").isNumber() ? person.path("branchId").asLong() : null);
        body.put("employeeRoleId", person.path("employeeRoleId").isNumber() ? person.path("employeeRoleId").asLong() : null);
        body.put("enabled", person.path("enabled").asBoolean()); return body;
    }
    private JsonNode update(String token, long id, Map<String,Object> body, int expected) throws Exception {
        String response = mvc.perform(put("/api/v1/auth/accounts/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? null : result(response);
    }
    private long role(String admin, String scope, List<String> permissions) throws Exception {
        return result(mvc.perform(post("/api/v1/roles").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", unique("role"), "scope", scope, "permissions", permissions))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }
    private JsonNode createBranch(String token, String kind) throws Exception {
        return result(mvc.perform(post("/api/v1/branches").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", unique("location"), "kind", kind))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode branchUpdate(String token, JsonNode branch, String name, boolean active, int expected) throws Exception {
        return result(mvc.perform(put("/api/v1/branches/" + branch.path("id").asLong()).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version", branch.path("version").asLong(), "name", name,
                        "kind", branch.path("kind").asText(), "active", active))))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
}
