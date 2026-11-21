package com.denizcan.stockorderpayment.service.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TrendyolStockGatewayTest {
    MockRestServiceServer server;TrendyolStockGateway gateway;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://stageapigw.trendyol.com");
        server=MockRestServiceServer.bindTo(builder).build();
        gateway=new TrendyolStockGateway(TrendyolStockGateway.authenticated(builder,"123","test-key","test-secret"),"123","STAGE","fixture");
    }
    @AfterEach void verify(){server.verify();}
    @Test void sendsOnlyQuantityWithRequiredHeadersAndReturnsBatchReceipt() {
        server.expect(requestTo("https://stageapigw.trendyol.com/integration/inventory/sellers/123/products/price-and-inventory"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Basic dGVzdC1rZXk6dGVzdC1zZWNyZXQ="))
                .andExpect(header("User-Agent","123 - SelfIntegration"))
                .andExpect(content().json("{\"items\":[{\"barcode\":\"869123\",\"quantity\":7}]}",true))
                .andRespond(withSuccess("{\"batchRequestId\":\"batch-123\"}",MediaType.APPLICATION_JSON));
        assertThat(gateway.submit(List.of(new TrendyolStockGateway.Item("869123",7)))).isEqualTo("batch-123");
    }
    @Test void stockBatchUsesEveryItemResultWithoutRequiringTopLevelStatus() throws Exception {
        var items=List.of(new TrendyolStockGateway.Item("a",7),new TrendyolStockGateway.Item("b",3));
        expectBatch("{\"items\":[{\"requestItem\":{\"barcode\":\"a\",\"quantity\":7},\"status\":\"SUCCESS\"}]}");
        expectBatch(new ObjectMapper().writeValueAsString(Map.of("items",List.of(
                Map.of("requestItem",Map.of("barcode","a","quantity",7),"status","SUCCESS"),
                Map.of("requestItem",Map.of("barcode","b","quantity",3),"status","SUCCESS")))));
        expectBatch("{\"items\":[{\"requestItem\":{\"barcode\":\"a\",\"quantity\":7},\"status\":\"FAILED\"}]}");
        assertThat(gateway.check("batch-123",items)).isEqualTo(TrendyolStockGateway.Outcome.PENDING);
        assertThat(gateway.check("batch-123",items)).isEqualTo(TrendyolStockGateway.Outcome.SUCCESS);
        assertThat(gateway.check("batch-123",items.subList(0,1))).isEqualTo(TrendyolStockGateway.Outcome.FAILED);
    }
    @Test void mismatchedOrDuplicateItemsAreNeverAcknowledgedAsSuccess() {
        expectBatch("{\"items\":[{\"requestItem\":{\"barcode\":\"a\",\"quantity\":99},\"status\":\"SUCCESS\"}]}");
        expectBatch("{\"items\":[{\"requestItem\":{\"barcode\":\"a\",\"quantity\":7.2},\"status\":\"SUCCESS\"}]}");
        expectBatch("{\"items\":[{\"requestItem\":{\"barcode\":\"a\",\"quantity\":7},\"status\":\"SUCCESS\"},{\"requestItem\":{\"barcode\":\"a\",\"quantity\":7},\"status\":\"SUCCESS\"}]}");
        assertThat(gateway.check("batch-123",List.of(new TrendyolStockGateway.Item("a",7)))).isEqualTo(TrendyolStockGateway.Outcome.UNCERTAIN);
        assertThat(gateway.check("batch-123",List.of(new TrendyolStockGateway.Item("a",7)))).isEqualTo(TrendyolStockGateway.Outcome.UNCERTAIN);
        assertThat(gateway.check("batch-123",List.of(new TrendyolStockGateway.Item("a",7)))).isEqualTo(TrendyolStockGateway.Outcome.UNCERTAIN);
    }
    @Test void rejectedRequestsDoNotExposeProviderResponseOrSecrets() {
        server.expect(requestTo("https://stageapigw.trendyol.com/integration/inventory/sellers/123/products/price-and-inventory"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private-provider-body test-secret"));
        assertThatThrownBy(()->gateway.submit(List.of(new TrendyolStockGateway.Item("a",1))))
                .isInstanceOf(TrendyolStockGateway.Rejected.class).hasMessageNotContaining("test-secret").hasMessageNotContaining("private-provider-body");
    }
    private void expectBatch(String body) {server.expect(requestTo("https://stageapigw.trendyol.com/integration/product/sellers/123/products/batch-requests/batch-123"))
            .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));}
}
