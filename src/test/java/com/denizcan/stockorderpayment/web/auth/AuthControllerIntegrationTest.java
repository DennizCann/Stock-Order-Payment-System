package com.denizcan.stockorderpayment.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.denizcan.stockorderpayment.service.auth.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountService accounts;

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "admin",
                "password", "admin123"
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "admin",
                "password", "wrong"
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorInvitesEmployeeAndOnlyNewestLinkCanActivateOnce() throws Exception {
        String username = "new-employee";
        String password = "employee-test-passphrase";
        String adminToken = token("admin", "admin123");
        mockMvc.perform(post("/api/v1/auth/accounts").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "role", "ADMIN"))))
                .andExpect(status().isBadRequest());
        var create = post("/api/v1/auth/accounts").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", username, "role", "USER")));
        mockMvc.perform(post("/api/v1/auth/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username))))
                .andExpect(status().is4xxClientError());
        String employeeToken = token("user", "user123");
        mockMvc.perform(post("/api/v1/auth/accounts").header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("username", username))))
                .andExpect(status().isForbidden());
        String oldLink = objectMapper.readTree(mockMvc.perform(create.header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "password", password))))
                .andExpect(status().isUnauthorized());
        String newLink = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("username", username))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", oldLink, "password", password))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", newLink, "password", password))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", newLink, "password", password))))
                .andExpect(status().isBadRequest());
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(login).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/operations/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/accounts").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("username", username))))
                .andExpect(status().isConflict());
    }

    @Test
    void bootstrappedAdministratorCanLoginAndStartupPreservesOriginalPassword() throws Exception {
        String username = "setup-admin";
        String password = "initial-test-passphrase";
        accounts.bootstrapAdmin(username, password);
        accounts.bootstrapAdmin(username, "replacement-passphrase");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void administratorAssignsAnEmployeeToASpecificBranch() throws Exception {
        String admin = "Bearer " + token("admin", "admin123");
        String employee = "Bearer " + token("user", "user123");
        String username = "branch-employee";
        String password = "branch-test-passphrase";
        String branchRequest = body(Map.of("name", "Kadıköy şubesi"));
        mockMvc.perform(post("/api/v1/branches").header("Authorization", employee)
                        .contentType(MediaType.APPLICATION_JSON).content(branchRequest))
                .andExpect(status().isForbidden());
        String created = mockMvc.perform(post("/api/v1/branches").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(branchRequest))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long branchId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(get("/api/v1/branches").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].name").value(hasItem("Kadıköy şubesi")));
        mockMvc.perform(post("/api/v1/branches").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(branchRequest))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/auth/accounts").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "role", "BRANCH_EMPLOYEE"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/accounts").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "role", "BRANCH_EMPLOYEE", "branchId", Long.MAX_VALUE))))
                .andExpect(status().isBadRequest());

        String invitation = mockMvc.perform(post("/api/v1/auth/accounts").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "role", "BRANCH_EMPLOYEE", "branchId", branchId))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String activationToken = objectMapper.readTree(invitation).get("token").asText();
        mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", activationToken, "password", password))))
                .andExpect(status().isNoContent());
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BRANCH_EMPLOYEE"))
                .andExpect(jsonPath("$.branchName").value("Kadıköy şubesi"))
                .andReturn().getResponse().getContentAsString();
        String branchEmployee = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/branches").header("Authorization", branchEmployee))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products").header("Authorization", branchEmployee))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchSalesAreScopedAuditableAndSafeToRetry() throws Exception {
        String admin = "Bearer " + token("admin", "admin123");
        long firstBranch = createBranch(admin, "Satış test şubesi");
        long otherBranch = createBranch(admin, "Diğer test şubesi");

        String product = mockMvc.perform(post("/api/v1/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                                "name", "Test kahve makinesi", "sku", "BRANCH-SALE-TEST", "price", "125.50"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(product).get("id").asLong();
        String centralStock = "/api/v1/products/" + productId + "/stock";
        mockMvc.perform(post(centralStock).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":9}"))
                .andExpect(status().isCreated());
        String firstStock = "/api/v1/branches/" + firstBranch + "/stock";
        String otherStock = "/api/v1/branches/" + otherBranch + "/stock";
        mockMvc.perform(put(firstStock + "/" + productId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(otherStock + "/" + productId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                .andExpect(status().isOk());

        String username = "branch-sales-employee";
        String password = "branch-sale-test-passphrase";
        String invitation = mockMvc.perform(post("/api/v1/auth/accounts").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "role", "BRANCH_EMPLOYEE", "branchId", firstBranch))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", objectMapper.readTree(invitation).get("token").asText(),
                                "password", password))))
                .andExpect(status().isNoContent());
        String employee = "Bearer " + token(username, password);

        String myStock = "/api/v1/branches/me/stock";
        String sales = "/api/v1/branches/me/sales";
        String movements = "/api/v1/branches/movements";
        mockMvc.perform(get(myStock).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(3));
        mockMvc.perform(get(otherStock).header("Authorization", employee)).andExpect(status().isForbidden());
        mockMvc.perform(get(movements).header("Authorization", employee)).andExpect(status().isForbidden());
        mockMvc.perform(get(myStock).header("Authorization", admin)).andExpect(status().isForbidden());

        UUID saleId = UUID.randomUUID();
        String sale = saleBody(productId, 2, saleId);
        String saved = mockMvc.perform(post(sales).header("Authorization", employee)
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isOk()).andExpect(jsonPath("$.newQuantity").value(1))
                .andReturn().getResponse().getContentAsString();
        int movementId = objectMapper.readTree(saved).get("id").asInt();
        mockMvc.perform(post(sales).header("Authorization", employee)
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(movementId));
        mockMvc.perform(post(sales).header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(productId, 1, saleId)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(sales).header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(productId, 2, UUID.randomUUID())))
                .andExpect(status().isConflict());
        mockMvc.perform(get(myStock).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(1));
        mockMvc.perform(get(otherStock).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(4));
        mockMvc.perform(get(centralStock).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(9));

        mockMvc.perform(get(movements).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(movementId))
                .andExpect(jsonPath("$[0].branchName").value("Satış test şubesi"))
                .andExpect(jsonPath("$[0].username").value(username))
                .andExpect(jsonPath("$[0].type").value("SALE"))
                .andExpect(jsonPath("$[0].change").value(-2))
                .andExpect(jsonPath("$[0].newQuantity").value(1))
                .andExpect(jsonPath("$[1].type").value("ADJUSTMENT"));
    }

    private long createBranch(String admin, String name) throws Exception {
        String result = mockMvc.perform(post("/api/v1/branches").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(result).get("id").asLong();
    }

    private String saleBody(long productId, int quantity, UUID saleId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("productId", productId, "quantity", quantity, "saleId", saleId));
    }

    private String token(String username, String password) throws Exception {
        String result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(result).get("accessToken").asText();
    }

    private String body(Map<String, String> data) throws Exception {
        return objectMapper.writeValueAsString(data);
    }
}
