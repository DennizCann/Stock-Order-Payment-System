package com.denizcan.stockorderpayment.web.product;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import com.denizcan.stockorderpayment.domain.user.*;
import com.denizcan.stockorderpayment.repository.branch.BranchRepository;
import com.denizcan.stockorderpayment.repository.user.EmployeeRoleRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.service.auth.AccountService;
import com.denizcan.stockorderpayment.service.stock.StockService;
import com.denizcan.stockorderpayment.service.inventory.StockMovementService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductInventoryWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BranchRepository branches;
    @Autowired EmployeeRoleRepository roles;
    @Autowired ProductRepository products;
    @Autowired AccountService accounts;
    @Autowired StockService stock;
    @Autowired StockMovementService movements;

    @Test
    void createsProductQuantityAndTagsTogetherAndEditsOnlyItsOwnTags() throws Exception {
        String admin = login("admin", "admin123");
        JsonNode first = create(admin, 8, List.of(tag("Marka", "Bosch"), tag("Kategori", "Bahçe")));
        JsonNode second = create(admin, 0, List.of(tag("Marka", "Bosch")));
        long id = first.path("id").asLong();
        assertThat(first.path("quantity").asLong()).isEqualTo(8);
        assertThat(first.path("tags").size()).isEqualTo(2);
        mvc.perform(put("/api/v1/products/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Düzenlenen ürün", "sku", first.path("sku").asText(),
                        "price", 10, "version", first.path("version").asLong(), "tags", List.of(tag("Marka", "Bosch"), tag("Malzeme", "Ahşap"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(8));
        mvc.perform(get("/api/v1/products/" + id).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags.length()").value(2));
        mvc.perform(get("/api/v1/products/" + second.path("id").asLong()).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags[0].value").value("Bosch"));
        mvc.perform(get("/api/v1/products/tags").header("Authorization", admin)).andExpect(status().isOk());
        // Same tag pair is stored once, while the same name can have multiple values.
        create(admin, 0, List.of(tag("Renk", "Mavi"), tag("Renk", "Mavi"), tag("Renk", "Beyaz")));
    }

    @Test
    void centralAndBranchMovementsKeepProductTotalAndRejectInsufficientStockAtomically() throws Exception {
        String admin = login("admin", "admin123");
        long id = create(admin, 5, List.of()).path("id").asLong();
        long branch = branches.save(new Branch("Kartal-" + UUID.randomUUID())).getId();
        move(admin, id, branch, "IN", 10, 201);
        move(admin, id, null, "OUT", 2, 201);
        move(admin, id, branch, "OUT", 1, 201);
        assertQuantity(admin, id, 12);
        move(admin, id, branch, "OUT", 99, 409);
        assertQuantity(admin, id, 12);
        mvc.perform(get("/api/v1/stock-movements").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].createdAt").exists());
        mvc.perform(post("/api/v1/stock-movements").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("productId", id, "direction", "IN", "quantity", 0))))
                .andExpect(status().isBadRequest());
        stock.reserve(id, 1, "Test siparişi");
        assertQuantity(admin, id, 12);
        stock.consumeReserved(id, 1, "Test satışı");
        assertQuantity(admin, id, 11);
    }

    @Test
    void archivePreservesHistoryAndRequiresEmptyInventory() throws Exception {
        String admin = login("admin", "admin123");
        long id = create(admin, 2, List.of(tag("Marka", "Siemens"))).path("id").asLong();
        mvc.perform(delete("/api/v1/products/" + id).header("Authorization", admin)).andExpect(status().isConflict());
        move(admin, id, null, "OUT", 2, 201);
        mvc.perform(delete("/api/v1/products/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/products/" + id).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        move(admin, id, null, "IN", 1, 409);
        String all = mvc.perform(get("/api/v1/stock-movements").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(all)).anySatisfy(m -> assertThat(m.path("productId").asLong()).isEqualTo(id));
    }

    @Test
    void existingUserGetsNoNewWritePermissionsAndRoleGrantsApplyToExistingToken() throws Exception {
        String admin = login("admin", "admin123"), user = login("user", "user123");
        long id = create(admin, 0, List.of()).path("id").asLong();
        move(user, id, null, "IN", 1, 403);
        mvc.perform(delete("/api/v1/products/" + id).header("Authorization", user)).andExpect(status().isForbidden());
        var role = roles.save(new EmployeeRole("Stok görevlisi " + UUID.randomUUID(), Role.USER,
                Set.of(Permission.STOCK_VIEW)));
        String employee = employee(role, null);
        move(employee, id, null, "IN", 1, 403);
        mvc.perform(put("/api/v1/roles/" + role.getId()).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", role.getName(), "scope", "USER", "version", roles.findById(role.getId()).orElseThrow().getVersion(),
                        "permissions", List.of("STOCK_VIEW", "STOCK_MANAGE"))))).andExpect(status().isOk());
        move(employee, id, null, "IN", 1, 201);
        mvc.perform(put("/api/v1/roles/" + role.getId()).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", role.getName(), "scope", "USER", "version", roles.findById(role.getId()).orElseThrow().getVersion(),
                        "permissions", List.of("STOCK_VIEW"))))).andExpect(status().isOk());
        move(employee, id, null, "IN", 1, 403);
        assertQuantity(admin, id, 1);
    }

    @Test
    void branchStockManagerCannotTargetOrReadOtherBranchesOrCentralStock() throws Exception {
        String admin = login("admin", "admin123");
        long id = create(admin, 0, List.of()).path("id").asLong();
        long own = branches.save(new Branch("Own " + UUID.randomUUID())).getId();
        long other = branches.save(new Branch("Other " + UUID.randomUUID())).getId();
        var role = roles.save(new EmployeeRole("Şube stok görevlisi " + UUID.randomUUID(), Role.BRANCH_EMPLOYEE,
                Set.of(Permission.BRANCH_STOCK_VIEW, Permission.BRANCH_STOCK_MANAGE)));
        String employee = employee(role, own);
        move(admin, id, other, "IN", 3, 201);
        move(employee, id, own, "IN", 2, 201);
        move(employee, id, other, "IN", 5, 403);
        move(employee, id, null, "IN", 5, 403);
        String history = mvc.perform(get("/api/v1/stock-movements").header("Authorization", employee))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(history)).allSatisfy(m -> assertThat(m.path("branchId").asLong()).isEqualTo(own));
        mvc.perform(get("/api/v1/stock-movements/locations").header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertQuantity(admin, id, 5);
    }

    @Test
    void concurrentCentralAndBranchChangesDoNotLoseProductTotal() throws Exception {
        String admin = login("admin", "admin123");
        long id = create(admin, 0, List.of()).path("id").asLong();
        long branch = branches.save(new Branch("Concurrent " + UUID.randomUUID())).getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> movements.create("admin", id, null, StockMovementService.Direction.IN, 5, null, null));
            var b = pool.submit(() -> movements.create("admin", id, branch, StockMovementService.Direction.IN, 7, null, null));
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertQuantity(admin, id, 12);
    }

    private Map<String, String> tag(String name, String value) { return Map.of("name", name, "value", value); }
    private JsonNode create(String admin, int quantity, List<Map<String, String>> tags) throws Exception {
        String result = mvc.perform(post("/api/v1/products").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Kahve makinesi", "sku", UUID.randomUUID().toString(),
                        "price", 10, "quantity", quantity, "tags", tags))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result);
    }
    private void move(String token, long productId, Long branchId, String direction, int quantity, int status) throws Exception {
        Map<String, Object> body = new HashMap<>(); body.put("productId", productId); body.put("branchId", branchId);
        body.put("direction", direction); body.put("quantity", quantity);
        mvc.perform(post("/api/v1/stock-movements").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is(status));
    }
    private void assertQuantity(String token, long id, int expected) throws Exception {
        mvc.perform(get("/api/v1/products/" + id).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(expected));
    }
    private String employee(EmployeeRole role, Long branchId) throws Exception {
        String username = "u" + UUID.randomUUID().toString().replace("-", "");
        String password = "WorkflowTest12345";
        String activation = accounts.invite(username, role.getScope(), branchId, role.getId(), Set.of(), Set.of());
        accounts.activate(activation, password); return login(username, password);
    }
    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).path("accessToken").asText();
    }
}
