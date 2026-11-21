package com.denizcan.stockorderpayment.web.auth;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RoleRevisionIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Test void staleRoleAndInvitationCannotSilentlyApplyChangedPermissions()throws Exception {
        String admin=login();JsonNode role=create(admin);long id=role.path("id").asLong();
        Map<String,Object> update=new HashMap<>(Map.of("name",role.path("name").asText(),"scope","USER","permissions",List.of("PRODUCTS_VIEW","STOCK_VIEW")));
        call(admin,"PUT","/roles/"+id,update,409);update.put("version",role.path("version").asLong());
        JsonNode current=call(admin,"PUT","/roles/"+id,update,200);
        assertThat(current.path("version").asLong()).isGreaterThan(role.path("version").asLong());
        call(admin,"PUT","/roles/"+id,update,409);
        Map<String,Object> invite=new HashMap<>(Map.of("username","role-rev-"+UUID.randomUUID(),"employeeRoleId",id,"roleVersion",role.path("version").asLong()));
        call(admin,"POST","/auth/accounts",invite,409);invite.put("roleVersion",current.path("version").asLong());
        call(admin,"POST","/auth/accounts",invite,200);
    }
    @Test void concurrentRoleEditsWithSameRevisionHaveOneWinner()throws Exception {
        String admin=login();JsonNode role=create(admin);long id=role.path("id").asLong();
        var body=Map.of("name",role.path("name").asText(),"scope","USER","permissions",List.of("PRODUCTS_VIEW","STOCK_VIEW"),"version",role.path("version").asLong());
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Integer> edit=()->{start.await();return mvc.perform(put("/api/v1/roles/"+id).header("Authorization","Bearer "+admin)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andReturn().getResponse().getStatus();};
            var first=pool.submit(edit);var second=pool.submit(edit);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }finally{pool.shutdownNow();}
    }
    private JsonNode create(String admin)throws Exception{return call(admin,"POST","/roles",Map.of("name","Revision "+UUID.randomUUID(),"scope","USER","permissions",List.of("PRODUCTS_VIEW")),201);}
    private String login()throws Exception{return call(null,"POST","/auth/login",Map.of("username","admin","password","admin123"),200).path("accessToken").asText();}
    private JsonNode call(String token,String method,String path,Object body,int expected)throws Exception {
        var request=request(HttpMethod.valueOf(method),"/api/v1"+path).contentType(MediaType.APPLICATION_JSON);
        if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.content(json.writeValueAsBytes(body));
        var response=mvc.perform(request).andReturn().getResponse();assertThat(response.getStatus()).as(method+" "+path+" "+response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank()?json.nullNode():json.readTree(response.getContentAsString());
    }
}
