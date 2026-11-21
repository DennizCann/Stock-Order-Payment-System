package com.denizcan.stockorderpayment.web.auth;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FlexiblePermissionsIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;String admin;
    @BeforeEach void setup()throws Exception{admin=login("admin","admin123");}
    @Test void branchRoleCombinesCatalogEditingAndOwnSalesWithoutSeeingGlobalStock()throws Exception {
        long own=branch(),other=branch(),product=product(6);call(admin,"PUT","/branches/"+own+"/stock/"+product,Map.of("quantity",3),200);
        JsonNode role=role("BRANCH_EMPLOYEE",List.of("PRODUCTS_VIEW","PRODUCTS_CREATE","PRODUCTS_EDIT","BRANCH_STOCK_VIEW","BRANCH_SALE_CREATE"));
        var worker=worker(role,own);String token=worker.token();
        JsonNode visible=call(token,"GET","/products/"+product,null,200);assertThat(visible.path("quantity").isNull()).isTrue();
        JsonNode created=call(token,"POST","/products",Map.of("name","Branch-created catalog entry","sku",UUID.randomUUID().toString(),"quantity",0),201);
        assertThat(created.path("quantity").isNull()).isTrue();
        call(token,"PUT","/products/"+created.path("id").asLong(),Map.of("name","Edited by branch worker","sku",created.path("sku").asText(),"version",created.path("version").asLong(),"tags",List.of(Map.of("name","Marka","value","Arçelik"))),200);
        call(token,"POST","/products",Map.of("name","Forbidden initial stock","sku",UUID.randomUUID().toString(),"quantity",1),403);
        call(token,"POST","/branches/me/sales",Map.of("productId",product,"quantity",1,"saleId",UUID.randomUUID()),200);
        call(token,"GET","/branches/"+other+"/stock",null,403);
        assertThat(call(admin,"GET","/products/"+product,null,200).path("quantity").asInt()).isEqualTo(8);
    }
    @Test void customRoleHasOwnBranchPermissionsIndependentOfLegacyAccountType()throws Exception {
        long own=branch(),other=branch(),product=product(0);JsonNode role=role("USER",List.of("BRANCH_STOCK_VIEW","BRANCH_STOCK_MANAGE"));
        call(admin,"POST","/auth/accounts",Map.of("username","no-branch-"+UUID.randomUUID(),"role","USER","employeeRoleId",role.path("id").asLong()),400);
        var worker=worker(role,own);call(worker.token(),"GET","/branches/me/stock",null,200);
        operation(worker.token(),"IN",null,own,product,2,201);operation(worker.token(),"IN",null,other,product,2,403);
        call(worker.token(),"GET","/products/"+product+"/stock",null,403);
        assertThat(call(worker.token(),"GET","/inventory-operations",null,200)).allSatisfy(o->assertThat(o.path("targetBranchId").asLong()).isEqualTo(own));
    }
    @Test void explicitlyGrantedGlobalManagementWorksForAnAssignedBranchWorker()throws Exception {
        long own=branch(),product=product(8);var permissions=new ArrayList<>(List.of("STOCK_VIEW","STOCK_MANAGE","BRANCHES_VIEW","BRANCHES_MANAGE"));
        JsonNode role=role("BRANCH_EMPLOYEE",permissions);var worker=worker(role,own);
        call(worker.token(),"GET","/branches",null,200);operation(worker.token(),"IN",null,null,product,2,201);
        assertThat(call(worker.token(),"GET","/products/"+product,null,200).path("quantity").asInt()).isEqualTo(10);
        long target=call(worker.token(),"POST","/branches",Map.of("name","Delegated warehouse "+UUID.randomUUID(),"kind","WAREHOUSE"),201).path("id").asLong();
        operation(worker.token(),"TRANSFER",null,target,product,1,403);
        permissions.add("STOCK_TRANSFER");updateRole(role,permissions,200);
        operation(worker.token(),"TRANSFER",null,target,product,1,201);
    }
    @Test void globalViewingDoesNotTurnOwnBranchManagementIntoGlobalWriting()throws Exception {
        long own=branch(),other=branch(),product=product(4);JsonNode role=role("USER",List.of("STOCK_VIEW","BRANCH_STOCK_VIEW","BRANCH_STOCK_MANAGE"));var worker=worker(role,own);
        call(worker.token(),"GET","/products/"+product+"/stock",null,200);operation(worker.token(),"IN",null,own,product,2,201);
        operation(worker.token(),"IN",null,null,product,2,403);operation(worker.token(),"IN",null,other,product,2,403);
    }
    @Test void sharedRoleAndPersonalOverridesKeepLocationRequirementsConsistent()throws Exception {
        long own=branch();JsonNode role=role("USER",List.of("PRODUCTS_VIEW"));var worker=worker(role,null);
        updateRole(role,List.of("PRODUCTS_VIEW","BRANCH_STOCK_VIEW"),409);
        JsonNode account=account(worker.id());updateAccount(account,role,own,200);
        updateRole(role,List.of("PRODUCTS_VIEW","BRANCH_STOCK_VIEW"),200);
        call(login(worker.username(),"deneme123456789"),"GET","/branches/me/stock",null,200);
        updateAccount(account(worker.id()),role,null,400);

        JsonNode plain=role("USER",List.of("PRODUCTS_VIEW"));var personal=worker(plain,own);JsonNode before=account(personal.id());
        call(admin,"PUT","/auth/accounts/"+personal.id()+"/permissions",Map.of("revision",before.path("revision").asText(),"permissions",List.of("PRODUCTS_VIEW","BRANCH_STOCK_VIEW")),200);
        updateAccount(account(personal.id()),plain,null,200);
        call(login(personal.username(),"deneme123456789"),"GET","/branches/me/stock",null,403);
    }
    record Worker(long id,String username,String token){}
    private Worker worker(JsonNode role,Long branch)throws Exception {
        String name="flex-"+UUID.randomUUID();var body=new HashMap<String,Object>();body.put("username",name);body.put("role",role.path("scope").asText());body.put("employeeRoleId",role.path("id").asLong());body.put("branchId",branch);
        var invite=call(admin,"POST","/auth/accounts",body,200);call(null,"POST","/auth/activate",Map.of("token",invite.path("token").asText(),"password","deneme123456789"),204);
        JsonNode account=null;for(JsonNode candidate:call(admin,"GET","/auth/accounts",null,200))if(candidate.path("username").asText().equals(name))account=candidate;
        return new Worker(account.path("id").asLong(),name,login(name,"deneme123456789"));
    }
    private JsonNode role(String scope,List<String> permissions)throws Exception{return call(admin,"POST","/roles",Map.of("name","Role "+UUID.randomUUID(),"scope",scope,"permissions",permissions),201);}
    private void updateRole(JsonNode role,List<String> permissions,int status)throws Exception {call(admin,"PUT","/roles/"+role.path("id").asLong(),Map.of("name",role.path("name").asText(),"scope",role.path("scope").asText(),"permissions",permissions,"version",role.path("version").asLong()),status);}
    private JsonNode account(long id)throws Exception {for(JsonNode p:call(admin,"GET","/auth/accounts",null,200))if(p.path("id").asLong()==id)return p;throw new AssertionError();}
    private void updateAccount(JsonNode account,JsonNode role,Long branch,int expected)throws Exception {
        var body=new HashMap<String,Object>();body.put("revision",account.path("revision").asText());body.put("username",account.path("username").asText());body.put("role",role.path("scope").asText());body.put("employeeRoleId",role.path("id").asLong());body.put("branchId",branch);body.put("enabled",true);
        call(admin,"PUT","/auth/accounts/"+account.path("id").asLong(),body,expected);
    }
    private long branch()throws Exception{return call(admin,"POST","/branches",Map.of("name","Flex branch "+UUID.randomUUID()),201).path("id").asLong();}
    private long product(int quantity)throws Exception{return call(admin,"POST","/products",Map.of("name","Flex product","sku",UUID.randomUUID().toString(),"quantity",quantity),201).path("id").asLong();}
    private void operation(String actor,String kind,Long source,Long target,long product,int quantity,int status)throws Exception {
        var body=new HashMap<String,Object>();body.put("requestId",UUID.randomUUID());body.put("kind",kind);body.put("sourceBranchId",source);body.put("targetBranchId",target);body.put("items",List.of(Map.of("productId",product,"quantity",quantity)));
        call(actor,"POST","/inventory-operations",body,status);
    }
    private String login(String name,String password)throws Exception{return call(null,"POST","/auth/login",Map.of("username",name,"password",password),200).path("accessToken").asText();}
    private JsonNode call(String token,String method,String path,Object body,int expected)throws Exception {
        var request=request(HttpMethod.valueOf(method),"/api/v1"+path).contentType(MediaType.APPLICATION_JSON);if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.content(json.writeValueAsBytes(body));var result=mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(method+" "+path+" "+result.getResponse().getContentAsString()).isEqualTo(expected);
        String text=result.getResponse().getContentAsString();return text.isBlank()?json.nullNode():json.readTree(text);
    }
}
