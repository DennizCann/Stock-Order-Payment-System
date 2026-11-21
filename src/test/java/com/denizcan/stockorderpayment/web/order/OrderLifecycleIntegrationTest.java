package com.denizcan.stockorderpayment.web.order;

import com.denizcan.stockorderpayment.domain.order.OrderStatus;
import com.denizcan.stockorderpayment.service.order.OrderLifecycleService;
import com.denizcan.stockorderpayment.service.stock.StockService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OrderLifecycleIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired OrderLifecycleService lifecycle;@Autowired StockService stock;
    String admin;
    @BeforeEach void setup()throws Exception {admin=login("admin","admin123");}
    @Test void shipmentConsumesReservedAndTotalStockOnceAndPreservesAudit()throws Exception {
        long a=product(10);long order=order(List.of(item(a,3)));
        change(admin,order,"CREATED","SHIPPED",200);stock(a,7,0);total(a,7);
        change(admin,order,"CREATED","SHIPPED",200);stock(a,7,0);total(a,7);
        change(admin,order,"CREATED","CANCELLED",409);
        JsonNode history=call(admin,"GET","/operations/orders/"+order+"/history",null,200);
        assertThat(history).hasSize(1);assertThat(history.get(0).path("username").asText()).isEqualTo("admin");
        assertThat(history.get(0).path("previousStatus").asText()).isEqualTo("CREATED");
    }
    @Test void cancellationReleasesReservationWithoutChangingPhysicalQuantity()throws Exception {
        long a=product(10),order=order(List.of(item(a,4)));stock(a,10,4);
        change(admin,order,"PAID","CANCELLED",409);stock(a,10,4);
        change(admin,order,"CREATED","CANCELLED",200);stock(a,10,0);total(a,10);
        change(admin,order,"CREATED","CANCELLED",200);stock(a,10,0);
        change(admin,order,"CANCELLED","SHIPPED",409);
    }
    @Test void aFailedLineRollsBackEveryShipmentStockAndHistoryUpdate()throws Exception {
        long a=product(10),b=product(10),order=order(List.of(item(a,2),item(b,2)));
        stock.release(b,2,"Simulated inconsistent reservation");
        change(admin,order,"CREATED","SHIPPED",400);stock(a,10,2);total(a,10);stock(b,10,0);
        assertThat(call(admin,"GET","/orders/"+order,null,200).path("status").asText()).isEqualTo("CREATED");
        assertThat(call(admin,"GET","/operations/orders/"+order+"/history",null,200)).isEmpty();
    }
    @Test void concurrentShipmentAndCancellationHaveOnlyOneWinner()throws Exception {
        long a=product(10),order=order(List.of(item(a,2)));ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            var first=pool.submit(()->{start.await();return attempt(order,OrderStatus.SHIPPED);});
            var second=pool.submit(()->{start.await();return attempt(order,OrderStatus.CANCELLED);});start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }finally{start.countDown();pool.shutdownNow();}
        assertThat(call(admin,"GET","/operations/orders/"+order+"/history",null,200)).hasSize(1);
        JsonNode s=call(admin,"GET","/products/"+a+"/stock",null,200);assertThat(s.path("reservedQuantity").asInt()).isZero();
        assertThat(s.path("quantity").asInt()).isIn(8,10);
    }
    @Test void operationsAccessIsSeparateFromOwnOrderAccessAndCanBeDelegated()throws Exception {
        long a=product(10),order=order(List.of(item(a,2)));
        String reader=employee(List.of("OPERATIONS_ORDERS_VIEW"));call(reader,"GET","/operations/orders",null,200);
        call(reader,"GET","/operations/orders/"+order+"/history",null,200);change(reader,order,"CREATED","SHIPPED",403);
        String manager=employee(List.of("OPERATIONS_ORDERS_VIEW","OPERATIONS_ORDERS_MANAGE"));change(manager,order,"CREATED","SHIPPED",200);
        String regular=login("user","user123");call(regular,"GET","/operations/orders",null,403);
        call(regular,"GET","/operations/orders/"+order+"/history",null,403);change(regular,order,"SHIPPED","CANCELLED",403);
    }
    private int attempt(long order,OrderStatus target) {try{lifecycle.change(order,OrderStatus.CREATED,target,"admin",null);return 200;}catch(ResponseStatusException conflict){return conflict.getStatusCode().value();}}
    private Map<String,Object> item(long product,int qty){return Map.of("productId",product,"quantity",qty);}
    private long order(List<Map<String,Object>> items)throws Exception{return call(admin,"POST","/orders",Map.of("items",items),201).path("id").asLong();}
    private long product(int qty)throws Exception{return call(admin,"POST","/products",Map.of("name","Lifecycle product","sku",UUID.randomUUID().toString(),"price",10,"quantity",qty),201).path("id").asLong();}
    private void change(String actor,long id,String previous,String status,int expected)throws Exception {call(actor,"PUT","/operations/orders/"+id+"/state",Map.of("expectedStatus",previous,"status",status),expected);}
    private void stock(long id,int qty,int reserved)throws Exception {JsonNode s=call(admin,"GET","/products/"+id+"/stock",null,200);assertThat(s.path("quantity").asInt()).isEqualTo(qty);assertThat(s.path("reservedQuantity").asInt()).isEqualTo(reserved);}
    private void total(long id,int qty)throws Exception {assertThat(call(admin,"GET","/products/"+id,null,200).path("quantity").asInt()).isEqualTo(qty);}
    private String employee(List<String> permissions)throws Exception {
        long role=call(admin,"POST","/roles",Map.of("name",UUID.randomUUID().toString(),"scope","USER","permissions",permissions),201).path("id").asLong();
        String name="orders-"+UUID.randomUUID();var invite=call(admin,"POST","/auth/accounts",Map.of("username",name,"role","USER","employeeRoleId",role),200);
        call(null,"POST","/auth/activate",Map.of("token",invite.path("token").asText(),"password","deneme123456789"),204);return login(name,"deneme123456789");
    }
    private String login(String name,String password)throws Exception{return call(null,"POST","/auth/login",Map.of("username",name,"password",password),200).path("accessToken").asText();}
    private JsonNode call(String token,String method,String path,Object body,int expected)throws Exception {
        var request=request(HttpMethod.valueOf(method),"/api/v1"+path).contentType(MediaType.APPLICATION_JSON);
        if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.content(json.writeValueAsBytes(body));
        var result=mvc.perform(request).andReturn();assertThat(result.getResponse().getStatus()).as(method+" "+path+" "+result.getResponse().getContentAsString()).isEqualTo(expected);
        String text=result.getResponse().getContentAsString();return text.isBlank()?json.nullNode():json.readTree(text);
    }
}
