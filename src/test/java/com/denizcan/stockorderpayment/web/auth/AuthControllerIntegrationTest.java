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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        var create = post("/api/v1/auth/accounts").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", username, "role", "ADMIN")));
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
