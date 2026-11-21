package com.denizcan.stockorderpayment.web.product;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ProductBasicsIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;String admin;
    @BeforeEach void setup()throws Exception{admin=login("admin","admin123");}

    @Test void nameSkuAndQuantityAreEnoughAndNoBranchIsCreated()throws Exception {
        int locations=call(admin,"GET","/branches",null,200).size();String sku=UUID.randomUUID().toString();
        JsonNode product=call(admin,"POST","/products",Map.of("name","  Kahve makinesi  ","sku","  "+sku+"  ","quantity",4,
                "tags",List.of(Map.of("name","Marka","value","Arçelik"),Map.of("name","Kategori","value","Ev eşyası"))),201);
        assertThat(product.path("name").asText()).isEqualTo("Kahve makinesi");assertThat(product.path("sku").asText()).isEqualTo(sku);
        assertThat(product.path("price").isNull()).isTrue();assertThat(product.path("quantity").asInt()).isEqualTo(4);
        assertThat(call(admin,"GET","/products/"+product.path("id").asLong()+"/stock",null,200).path("quantity").asInt()).isEqualTo(4);
        assertThat(call(admin,"GET","/branches",null,200)).hasSize(locations);
        call(admin,"POST","/products",Map.of("name","Duplicate","sku"," "+sku+" "),409);
    }
    @Test void creatingProductsDoesNotBypassStockPermission()throws Exception {
        String creator=employee(List.of("PRODUCTS_VIEW","PRODUCTS_CREATE"));String sku=UUID.randomUUID().toString();
        call(creator,"POST","/products",Map.of("name","Unpriced","sku",sku,"quantity",5),403);
        assertThat(call(creator,"POST","/products",Map.of("name","Unpriced","sku",sku,"quantity",0),201).path("quantity").asInt()).isZero();
        call(creator,"POST","/products",Map.of("name","Unpriced","sku",UUID.randomUUID().toString()),201);
    }
    @Test void staleEditCannotOverwriteMetadataAndStockChangesDoNotInvalidateMetadataForm()throws Exception {
        JsonNode product=create(5);long id=product.path("id").asLong();long revision=product.path("version").asLong();
        call(admin,"POST","/products/"+id+"/stock/in",Map.of("quantity",2),200);
        JsonNode revised=update(product,"Yeni ad",revision,null,List.of(Map.of("name","Marka","value","Bosch")),200);
        assertThat(revised.path("quantity").asInt()).isEqualTo(7);assertThat(revised.path("version").asLong()).isGreaterThan(revision);
        update(product,"Eski form",revision,null,List.of(),409);
        assertThat(call(admin,"GET","/products/"+id,null,200).path("name").asText()).isEqualTo("Yeni ad");
    }
    @Test void archivedProductCanBeRestoredWithoutErasingTagsOrHistory()throws Exception {
        JsonNode product=create(0);product=update(product,"Arşiv ürünü",product.path("version").asLong(),null,List.of(Map.of("name","Marka","value","Arçelik")),200);
        long id=product.path("id").asLong();call(admin,"DELETE","/products/"+id,null,204);
        JsonNode archived=call(admin,"GET","/products/"+id,null,200);assertThat(archived.path("active").asBoolean()).isFalse();
        assertThat(call(admin,"GET","/products?includeArchived=true",null,200)).anySatisfy(p->assertThat(p.path("id").asLong()).isEqualTo(id));
        JsonNode restored=call(admin,"POST","/products/"+id+"/restore",null,200);
        assertThat(restored.path("active").asBoolean()).isTrue();assertThat(restored.path("tags")).hasSize(1);
        update(product,"Stale before archive",product.path("version").asLong(),null,List.of(),409);
        call(login("user","user123"),"POST","/products/"+id+"/restore",null,403);
    }
    @Test void unpricedProductsCannotCreateOrdersUntilAnExplicitPriceIsEntered()throws Exception {
        JsonNode product=create(4);long id=product.path("id").asLong();
        var order=Map.of("items",List.of(Map.of("productId",id,"quantity",2)));
        call(admin,"POST","/orders",order,400);
        assertThat(call(admin,"GET","/products/"+id+"/stock",null,200).path("reservedQuantity").asInt()).isZero();
        update(product,product.path("name").asText(),product.path("version").asLong(),10,List.of(),200);
        call(admin,"POST","/orders",order,201);
    }
    @Test void metadataUpdateRequiresRevisionAndRejectsQuantityAndInvalidPrices()throws Exception {
        JsonNode product=create(1);long id=product.path("id").asLong();
        var body=new HashMap<String,Object>();body.put("name","Test");body.put("sku",product.path("sku").asText());
        call(admin,"PUT","/products/"+id,body,400);body.put("version",product.path("version").asLong());body.put("quantity",55);
        call(admin,"PUT","/products/"+id,body,400);body.remove("quantity");body.put("price",-1);call(admin,"PUT","/products/"+id,body,400);
        body.put("price",1.001);call(admin,"PUT","/products/"+id,body,400);
        assertThat(call(admin,"GET","/products/"+id,null,200).path("quantity").asInt()).isEqualTo(1);
    }
    @Test void concurrentMetadataAndStockUpdatesKeepBothChanges()throws Exception {
        JsonNode product=create(4);long id=product.path("id").asLong();ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            var edit=pool.submit(()->{start.await();return update(product,"Concurrent name",product.path("version").asLong(),null,List.of(),200);});
            var move=pool.submit(()->{start.await();return call(admin,"POST","/products/"+id+"/stock/in",Map.of("quantity",3),200);});start.countDown();
            edit.get(15,TimeUnit.SECONDS);move.get(15,TimeUnit.SECONDS);
        }finally{start.countDown();pool.shutdownNow();}
        JsonNode current=call(admin,"GET","/products/"+id,null,200);assertThat(current.path("name").asText()).isEqualTo("Concurrent name");assertThat(current.path("quantity").asInt()).isEqualTo(7);
    }
    private JsonNode create(int qty)throws Exception{return call(admin,"POST","/products",Map.of("name","Basic product","sku",UUID.randomUUID().toString(),"quantity",qty),201);}
    private JsonNode update(JsonNode p,String name,long version,Integer price,List<?> tags,int expected)throws Exception {
        var body=new HashMap<String,Object>();body.put("name",name);body.put("sku",p.path("sku").asText());body.put("version",version);body.put("price",price);body.put("tags",tags);
        return call(admin,"PUT","/products/"+p.path("id").asLong(),body,expected);
    }
    private String employee(List<String> permissions)throws Exception {
        long role=call(admin,"POST","/roles",Map.of("name",UUID.randomUUID().toString(),"scope","USER","permissions",permissions),201).path("id").asLong();
        String name="basics-"+UUID.randomUUID();var invite=call(admin,"POST","/auth/accounts",Map.of("username",name,"role","USER","employeeRoleId",role),200);
        call(null,"POST","/auth/activate",Map.of("token",invite.path("token").asText(),"password","deneme123456789"),204);return login(name,"deneme123456789");
    }
    private String login(String name,String password)throws Exception{return call(null,"POST","/auth/login",Map.of("username",name,"password",password),200).path("accessToken").asText();}
    private JsonNode call(String token,String method,String path,Object body,int expected)throws Exception {
        var request=request(HttpMethod.valueOf(method),"/api/v1"+path).contentType(MediaType.APPLICATION_JSON);if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.content(json.writeValueAsBytes(body));var result=mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(method+" "+path+" "+result.getResponse().getContentAsString()).isEqualTo(expected);
        String text=result.getResponse().getContentAsString();return text.isBlank()?json.nullNode():json.readTree(text);
    }
}
