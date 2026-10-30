package com.denizcan.stockorderpayment.web.order;

import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.domain.stock.Stock;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired UserRepository users;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void authenticatedIdentityOverridesAnyClientSuppliedUserId() throws Exception {
        Product product = products.save(new Product("Demo", UUID.randomUUID().toString(), new BigDecimal("25.00")));
        stocks.save(new Stock(product.getId(), 4));
        Long accountId = users.findByUsername("user").orElseThrow().getId();
        String body = json.writeValueAsString(Map.of("userId", 999999L,
                "items", List.of(Map.of("productId", product.getId(), "quantity", 2))));
        String response = mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.userId").value(accountId))
                .andExpect(jsonPath("$.totalAmount").value(50.0))
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(response).get("id").asLong();
        mvc.perform(get("/api/v1/orders/" + orderId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/" + orderId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        assertThat(stocks.findByProductId(product.getId()).orElseThrow().getReservedQuantity()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void rejectsInvalidItems() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCannotPlaceOrder() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isForbidden());
    }
}
