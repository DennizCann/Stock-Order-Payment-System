package com.denizcan.stockorderpayment.web.stock;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class StockRevisionIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Test void centralCountRejectsMissingOrStaleRevisionAfterMovementAndKeepsTotals() throws Exception {
        String admin=login();long id=product(admin,10);String path="/products/"+id+"/stock";
        JsonNode before=call(admin,"GET",path,null,200);
        call(admin,"PATCH",path,Map.of("quantity",20),409);
        call(admin,"POST",path+"/out",Map.of("quantity",2),200);
        call(admin,"PATCH",path,Map.of("quantity",20,"version",before.path("version").asLong()),409);
        JsonNode current=call(admin,"GET",path,null,200);assertThat(current.path("quantity").asInt()).isEqualTo(8);
        JsonNode updated=call(admin,"PATCH",path,Map.of("quantity",7,"version",current.path("version").asLong()),200);
        assertThat(updated.path("version").asLong()).isGreaterThan(current.path("version").asLong());
        assertThat(call(admin,"GET","/products/"+id,null,200).path("quantity").asLong()).isEqualTo(7);
    }
    @Test void branchCountRejectsStaleRevisionAfterTransferAndPreservesAggregate() throws Exception {
        String admin=login();long id=product(admin,10);
        long branch=call(admin,"POST","/branches",Map.of("name","Revision "+UUID.randomUUID()),201).path("id").asLong();
        String path="/branches/"+branch+"/stock/"+id;
        JsonNode before=call(admin,"PUT",path,Map.of("quantity",3),200);
        var operation=new HashMap<String,Object>();operation.put("requestId",UUID.randomUUID());operation.put("kind","TRANSFER");
        operation.put("targetBranchId",branch);operation.put("items",List.of(Map.of("productId",id,"quantity",2)));
        call(admin,"POST","/inventory-operations",operation,201);
        call(admin,"PUT",path,Map.of("quantity",1),409);
        call(admin,"PUT",path,Map.of("quantity",1,"version",before.path("version").asLong()),409);
        JsonNode current=call(admin,"GET","/branches/"+branch+"/stock",null,200).get(0);
        assertThat(current.path("quantity").asInt()).isEqualTo(5);
        JsonNode updated=call(admin,"PUT",path,Map.of("quantity",4,"version",current.path("version").asLong()),200);
        assertThat(updated.path("version").asLong()).isGreaterThan(current.path("version").asLong());
        assertThat(call(admin,"GET","/products/"+id,null,200).path("quantity").asLong()).isEqualTo(12);
    }
    private long product(String admin,int quantity)throws Exception{return call(admin,"POST","/products",Map.of("name","Revision product","sku",UUID.randomUUID().toString(),"quantity",quantity),201).path("id").asLong();}
    private String login()throws Exception{return call(null,"POST","/auth/login",Map.of("username","admin","password","admin123"),200).path("accessToken").asText();}
    private JsonNode call(String token,String method,String path,Object body,int expected)throws Exception {
        var request=request(HttpMethod.valueOf(method),"/api/v1"+path).contentType(MediaType.APPLICATION_JSON);
        if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.content(json.writeValueAsBytes(body));
        var response=mvc.perform(request).andReturn().getResponse();assertThat(response.getStatus()).as(method+" "+path+" "+response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank()?json.nullNode():json.readTree(response.getContentAsString());
    }
}
