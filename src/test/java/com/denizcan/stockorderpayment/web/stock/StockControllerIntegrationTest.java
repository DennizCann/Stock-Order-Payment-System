package com.denizcan.stockorderpayment.web.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGetAndUpdateStock() throws Exception {
        String productBody = objectMapper.writeValueAsString(Map.of(
                "name", "Mouse",
                "sku", "MOU-001",
                "price", 150
        ));

        MvcResult createProduct = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode productJson = objectMapper.readTree(createProduct.getResponse().getContentAsString());
        long productId = productJson.get("id").asLong();

        mockMvc.perform(post("/api/v1/products/" + productId + "/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 40))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(40));

        mockMvc.perform(get("/api/v1/products/" + productId + "/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId));

        mockMvc.perform(patch("/api/v1/products/" + productId + "/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 25))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(25));
    }
}
