package com.denizcan.stockorderpayment.web.auth;
import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.denizcan.stockorderpayment.repository.branch.BranchRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:owner_first_core;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true"})
@AutoConfigureMockMvc @ActiveProfiles("dev")
class OwnerFirstInstallationIntegrationTest {
    @Autowired UserRepository users;@Autowired BranchRepository branches;@Autowired ProductRepository products;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    @Test void freshInstallationHasOnlyOwnerAndCanStartInventoryWithoutEmployeesOrBranches()throws Exception {
        assertThat(users.findAll()).singleElement().satisfies(u->assertThat(u.getRole()).isEqualTo(Role.ADMIN));
        assertThat(branches.count()).isZero();assertThat(products.count()).isZero();
        String token=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        mvc.perform(post("/api/v1/products").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"İlk ürün\",\"sku\":\"FIRST-001\",\"quantity\":3}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(3));
        assertThat(branches.count()).isZero();assertThat(users.count()).isEqualTo(1);
        JsonNode branch=json.readTree(mvc.perform(post("/api/v1/branches").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kartal\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        // Exercise the real migration constraints, not Hibernate's create-drop schema.
        mvc.perform(post("/api/v1/auth/accounts").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"optional-branch\",\"role\":\"USER\",\"branchId\":"+branch.path("id").asLong()+"}"))
                .andExpect(status().isOk());
    }
}
