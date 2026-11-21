package com.denizcan.stockorderpayment.web.inventory;

import com.denizcan.stockorderpayment.domain.inventory.StockOperation;
import com.denizcan.stockorderpayment.repository.inventory.StockOperationRepository;
import com.denizcan.stockorderpayment.service.inventory.InventoryOperationService;
import com.denizcan.stockorderpayment.service.stock.StockService;
import com.denizcan.stockorderpayment.web.inventory.dto.InventoryOperationRequest;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class BatchInventoryOperationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired InventoryOperationService service;
    @Autowired StockOperationRepository operations;
    @Autowired StockService stock;

    @Test
    void batchTransferPreservesProductTotalsAndReplayIsIndependentOfItemOrder() throws Exception {
        String admin = login("admin", "admin123"); long a=product(admin,10), b=product(admin,20), destination=branch(admin);
        Map<String,Object> request = operation("TRANSFER", null, destination, List.of(item(a,3),item(b,4)));
        JsonNode receipt=execute(admin,request,201);
        total(admin,a,10); total(admin,b,20); central(admin,a,7); central(admin,b,16);
        assertThat(read(admin,"/api/v1/branches/"+destination+"/stock")).anySatisfy(s->{
            assertThat(s.path("productId").asLong()).isEqualTo(a); assertThat(s.path("quantity").asInt()).isEqualTo(3);
        });
        request.put("items",List.of(item(b,4),item(a,1),item(a,2)));
        assertThat(execute(admin,request,201).path("id").asText()).isEqualTo(receipt.path("id").asText());
        central(admin,a,7); total(admin,a,10);
        request.put("items",List.of(item(a,4),item(b,4))); execute(admin,request,409); central(admin,a,7);
    }

    @Test
    void failedSecondLineRollsBackEveryStockChangeAndReceipt() throws Exception {
        String admin=login("admin","admin123");long a=product(admin,5),b=product(admin,1),destination=branch(admin);
        Map<String,Object> request=operation("TRANSFER",null,destination,List.of(item(a,2),item(b,2)));
        execute(admin,request,409); central(admin,a,5); central(admin,b,1); total(admin,a,5);
        assertThat(operations.existsById(UUID.fromString(request.get("requestId").toString()))).isFalse();
        assertThat(read(admin,"/api/v1/branches/"+destination+"/stock")).isEmpty();
        stock.reserve(a,4,"Reserved for test");
        execute(admin,operation("TRANSFER",null,destination,List.of(item(a,2))),409); central(admin,a,5);
    }

    @Test
    void concurrentDuplicateCreatesOneReceiptAndOneStockChange() throws Exception {
        String admin=login("admin","admin123");long a=product(admin,0);
        var request=new InventoryOperationRequest(UUID.randomUUID(),StockOperation.Kind.IN,null,null,null,"Mal kabulü",null,
                List.of(new InventoryOperationRequest.Item(a,5)));
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<UUID> task=()->{start.await(5,TimeUnit.SECONDS);return service.create("admin",request).id();};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
        }finally{start.countDown();pool.shutdownNow();}
        total(admin,a,5);central(admin,a,5);
        assertThat(operations.findById(request.requestId()).orElseThrow().getLines()).hasSize(1);
        // New operation legs are not duplicated in the legacy history endpoint.
        assertThat(read(admin,"/api/v1/stock-movements")).noneSatisfy(m->assertThat(m.path("productId").asLong()).isEqualTo(a));
    }

    @Test
    void oppositeConcurrentTransfersUseConsistentLocks() throws Exception {
        String admin=login("admin","admin123");long a=product(admin,10),b=product(admin,10),branch=branch(admin);
        execute(admin,operation("IN",null,branch,List.of(item(a,10),item(b,10))),201);
        var outbound=new InventoryOperationRequest(UUID.randomUUID(),StockOperation.Kind.TRANSFER,null,branch,null,null,null,
                List.of(new InventoryOperationRequest.Item(a,3),new InventoryOperationRequest.Item(b,3)));
        var inbound=new InventoryOperationRequest(UUID.randomUUID(),StockOperation.Kind.TRANSFER,branch,null,null,null,null,
                List.of(new InventoryOperationRequest.Item(b,2),new InventoryOperationRequest.Item(a,2)));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{var first=pool.submit(()->service.create("admin",outbound));var second=pool.submit(()->service.create("admin",inbound));
            first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        central(admin,a,9);central(admin,b,9);total(admin,a,20);total(admin,b,20);
    }

    @Test
    void customTypeSnapshotsSurviveRenameAndArchiveAndReplayStillWorks() throws Exception {
        String admin=login("admin","admin123");long a=product(admin,4),branch=branch(admin);
        JsonNode type=post(admin,"/api/v1/inventory-operation-types",Map.of("name","Sevk "+UUID.randomUUID(),"kind","TRANSFER"),201);
        Map<String,Object> request=operation("TRANSFER",null,branch,List.of(item(a,1)));request.put("typeId",type.path("id").asLong());
        JsonNode receipt=execute(admin,request,201);String oldLabel=receipt.path("label").asText();
        JsonNode renamed=put(admin,"/api/v1/inventory-operation-types/"+type.path("id").asLong(),
                Map.of("name","Yeni sevk "+UUID.randomUUID(),"kind","TRANSFER","version",type.path("version").asLong(),"active",true),200);
        assertThat(execute(admin,request,201).path("label").asText()).isEqualTo(oldLabel);
        put(admin,"/api/v1/inventory-operation-types/"+type.path("id").asLong(),
                Map.of("name",renamed.path("name").asText(),"kind","TRANSFER","version",renamed.path("version").asLong(),"active",false),200);
        assertThat(execute(admin,request,201).path("label").asText()).isEqualTo(oldLabel);
        request.put("requestId",UUID.randomUUID().toString());execute(admin,request,409);
        central(admin,a,3);total(admin,a,4);
    }

    @Test
    void branchWorkersStayScopedAndTransferOnlyPermissionDoesNotGrantReceipts() throws Exception {
        String admin=login("admin","admin123");long a=product(admin,10),own=branch(admin),other=branch(admin);
        String employee=employee(admin,"BRANCH_EMPLOYEE",own,List.of("BRANCH_STOCK_VIEW","BRANCH_STOCK_MANAGE"));
        execute(employee,operation("IN",null,own,List.of(item(a,3))),201);
        execute(employee,operation("IN",null,other,List.of(item(a,1))),403);
        execute(employee,operation("TRANSFER",own,other,List.of(item(a,1))),403);
        execute(admin,operation("TRANSFER",own,other,List.of(item(a,1))),201);
        for(JsonNode receipt:read(employee,"/api/v1/inventory-operations")){
            assertThat(receipt.path("sourceBranchId").asLong()==own||receipt.path("targetBranchId").asLong()==own).isTrue();
            if(receipt.path("kind").asText().equals("TRANSFER")) assertThat(receipt.path("lines").get(0).path("targetRemaining").isNull()).isTrue();
        }
        String transferOnly=employee(admin,"USER",null,List.of("STOCK_VIEW","STOCK_TRANSFER"));
        execute(transferOnly,operation("TRANSFER",null,own,List.of(item(a,2))),201);
        execute(transferOnly,operation("IN",null,own,List.of(item(a,2))),403);
        post(employee,"/api/v1/inventory-operation-types",Map.of("name","Denied","kind","IN"),403);
        total(admin,a,13);
    }

    @Test
    void rejectsInvalidInputsAndFingerprintsAmbiguousLabelsWithoutChangingStock() throws Exception {
        String admin=login("admin","admin123");long a=product(admin,1);
        execute(admin,operation("TRANSFER",null,null,List.of(item(a,1))),400);
        execute(admin,operation("IN",null,null,List.of(item(a,0))),400);
        execute(admin,operation("IN",null,null,List.of(item(a,Integer.MAX_VALUE),item(a,1))),400);
        Map<String,Object> request=operation("IN",null,null,List.of(item(a,1)));request.put("label","a|b");request.put("note","c");execute(admin,request,201);
        request.put("label","a");request.put("note","b|c");execute(admin,request,409);total(admin,a,2);
    }

    private Map<String,Object> operation(String kind,Long source,Long target,List<Map<String,Object>> items){
        Map<String,Object> body=new HashMap<>();body.put("requestId",UUID.randomUUID().toString());body.put("kind",kind);
        body.put("sourceBranchId",source);body.put("targetBranchId",target);body.put("items",items);return body;
    }
    private Map<String,Object> item(long id,int amount){return Map.of("productId",id,"quantity",amount);}
    private JsonNode execute(String token,Map<String,Object> body,int expected)throws Exception{return post(token,"/api/v1/inventory-operations",body,expected);}
    private long product(String token,int quantity)throws Exception{return post(token,"/api/v1/products",Map.of("name","Item","sku",UUID.randomUUID().toString(),"price",1,"quantity",quantity),201).path("id").asLong();}
    private long branch(String token)throws Exception{return post(token,"/api/v1/branches",Map.of("name","Location "+UUID.randomUUID(),"kind","WAREHOUSE"),201).path("id").asLong();}
    private void total(String token,long id,int count)throws Exception{assertThat(read(token,"/api/v1/products/"+id).path("quantity").asInt()).isEqualTo(count);}
    private void central(String token,long id,int count)throws Exception{assertThat(read(token,"/api/v1/products/"+id+"/stock").path("quantity").asInt()).isEqualTo(count);}
    private JsonNode post(String token,String path,Object body,int expected)throws Exception{
        String value=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization",token==null?"":token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();return value.isBlank()?null:json.readTree(value);
    }
    private JsonNode put(String token,String path,Object body,int expected)throws Exception{
        String value=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();return json.readTree(value);
    }
    private JsonNode read(String token,String path)throws Exception{return json.readTree(mvc.perform(get(path).header("Authorization",token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private String login(String name,String password)throws Exception{return "Bearer "+post(null,"/api/v1/auth/login",Map.of("username",name,"password",password),200).path("accessToken").asText();}
    private String employee(String admin,String scope,Long branch,List<String> permissions)throws Exception{
        long role=post(admin,"/api/v1/roles",Map.of("name","Role "+UUID.randomUUID(),"scope",scope,"permissions",permissions),201).path("id").asLong();
        String username="worker-"+UUID.randomUUID();Map<String,Object> body=new HashMap<>();body.put("username",username);body.put("employeeRoleId",role);body.put("branchId",branch);
        JsonNode invitation=post(admin,"/api/v1/auth/accounts",body,200);
        post(null,"/api/v1/auth/activate",Map.of("token",invitation.path("token").asText(),"password","OperationTest12345"),204);return login(username,"OperationTest12345");
    }
}
