package com.denizcan.stockorderpayment.web.order;

import com.denizcan.stockorderpayment.domain.order.StockPublication;
import com.denizcan.stockorderpayment.repository.order.StockPublicationRepository;
import com.denizcan.stockorderpayment.service.order.*;
import com.denizcan.stockorderpayment.service.stock.StockService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties="app.trendyol.check-interval-ms=3600000") @AutoConfigureMockMvc @ActiveProfiles("test")
class ChannelStockIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired StockService stock;
    @Autowired ChannelStockService settings;@Autowired StockPublicationWorker worker;
    @Autowired StockPublicationRepository publications;@Autowired PlatformTransactionManager transactions;
    @MockBean TrendyolStockGateway gateway;
    String admin;
    @BeforeEach void setup() throws Exception {
        admin=login("admin","admin123");when(gateway.enabled()).thenReturn(true);
        when(gateway.connectionKey()).thenReturn(UUID.randomUUID().toString());when(gateway.mode()).thenReturn("STAGE");when(gateway.sellerId()).thenReturn("123");
        when(gateway.submit(anyList())).thenReturn("batch-test");when(gateway.check(anyString(),anyList())).thenReturn(TrendyolStockGateway.Outcome.SUCCESS);
        new TransactionTemplate(transactions).executeWithoutResult(s->{for(UUID id:publications.activeIds()) publications.lock(id).orElseThrow().finish(StockPublication.State.FAILED,"Test cleanup");});
    }
    @Test void limitsRespectReservationsSafetyAndDisabledProductsWithStaleEditProtection() throws Exception {
        long id=product(20);stock.reserve(id,4,"test order");String barcode=UUID.randomUUID().toString();
        policy(id,barcode,8,10,true,null,200);
        JsonNode p=policy(id);assertThat(p.path("available").asInt()).isEqualTo(16);assertThat(p.path("publishable").asInt()).isEqualTo(6);
        policy(id,barcode,2,0,false,p.path("version").asLong(),200);assertThat(policy(id).path("publishable").asInt()).isZero();
        policy(id,barcode,4,0,true,p.path("version").asLong(),409);
        policy(product(3),barcode,3,0,true,null,409);
    }
    @Test void receiptIsDurableAndOnlyConfirmedItemsPreventUnchangedResubmission() throws Exception {
        long a=product(10),b=product(10);policy(a,UUID.randomUUID().toString(),5,0,true,null,200);policy(b,UUID.randomUUID().toString(),4,0,true,null,200);
        UUID first=UUID.randomUUID();assertThat(publish(first,200).path("state").asText()).isEqualTo("SUBMITTED");
        assertThat(publish(first,200).path("id").asText()).isEqualTo(first.toString());verify(gateway,times(1)).submit(anyList());
        due(first);worker.process(first);assertThat(settings.get(first).state()).isEqualTo("SUCCESS");
        publish(UUID.randomUUID(),409);
        var p=policy(a);policy(a,p.path("barcode").asText(),3,0,true,p.path("version").asLong(),200);
        UUID second=UUID.randomUUID();publish(second,200);due(second);worker.process(second);
        assertThat(settings.get(second).lines()).hasSize(1);publish(UUID.randomUUID(),409);
    }
    @Test void unknownSubmissionNeverRetriesAutomaticallyAndRequiresExplicitReconciliation() throws Exception {
        long id=product(7);policy(id,UUID.randomUUID().toString(),5,0,true,null,200);
        when(gateway.submit(anyList())).thenThrow(new IllegalStateException("lost response with private data"));
        UUID request=UUID.randomUUID();JsonNode result=publish(request,200);
        assertThat(result.path("state").asText()).isEqualTo("UNCERTAIN");assertThat(result.toString()).doesNotContain("private data");
        worker.process(request);worker.process(request);verify(gateway,times(1)).submit(anyList());publish(UUID.randomUUID(),409);
        call(admin,"POST","/channel-stock/publications/"+request+"/resolve",Map.of("note","Checked seller panel","providerVerified",false),400);
        call(admin,"POST","/channel-stock/publications/"+request+"/resolve",Map.of("note","Checked seller panel","providerVerified",true),200);
        publish(UUID.randomUUID(),409); // The provider rejects repeated identical bodies for 15 minutes.
    }
    @Test void transientResultFailureRemainsPendingAndSurvivesAWorkerRetry() throws Exception {
        long id=product(5);policy(id,UUID.randomUUID().toString(),3,0,true,null,200);UUID request=UUID.randomUUID();publish(request,200);
        when(gateway.check(anyString(),anyList())).thenThrow(new IllegalStateException("offline"));due(request);worker.process(request);
        assertThat(settings.get(request).state()).isEqualTo("SUBMITTED");
        doReturn(TrendyolStockGateway.Outcome.SUCCESS).when(gateway).check(anyString(),anyList());due(request);worker.process(request);
        assertThat(settings.get(request).state()).isEqualTo("SUCCESS");verify(gateway,times(1)).submit(anyList());
    }
    @Test void staleSendingClaimIsNotSubmittedAgainAfterProcessRecovery() throws Exception {
        long id=product(5);policy(id,UUID.randomUUID().toString(),2,0,true,null,200);UUID request=settings.enqueue(UUID.randomUUID(),"admin");
        new TransactionTemplate(transactions).executeWithoutResult(s->{var p=publications.lock(request).orElseThrow();p.sending();ReflectionTestUtils.setField(p,"lastCheckedAt",Instant.now().minusSeconds(100));});
        worker.process(request);assertThat(settings.get(request).state()).isEqualTo("UNCERTAIN");verify(gateway,never()).submit(anyList());
    }
    @Test void channelViewingAndManagementCanBeDelegatedWithoutCatalogAccess() throws Exception {
        String reader=employee(List.of("CHANNELS_VIEW"));long id=product(2);
        call(reader,"GET","/channel-stock/policies",null,200);call(reader,"GET","/products",null,403);
        call(reader,"POST","/channel-stock/publications",Map.of("requestId",UUID.randomUUID()),403);
        String manager=employee(List.of("CHANNELS_VIEW","CHANNELS_MANAGE"));
        call(manager,"PUT","/channel-stock/policies/"+id,Map.of("barcode",UUID.randomUUID().toString(),"maximumQuantity",1,"safetyStock",0,"enabled",true),200);
        call(login("user","user123"),"GET","/channel-stock/policies",null,403);
    }
    @Test void disabledConnectionDoesNotSubmitAndNoCredentialsAreReturned() throws Exception {
        when(gateway.enabled()).thenReturn(false);publish(UUID.randomUUID(),409);verify(gateway,never()).submit(anyList());
        JsonNode connection=call(admin,"GET","/channel-stock/connection",null,200);
        assertThat(connection.fieldNames()).toIterable().containsExactlyInAnyOrder("configured","environment","sellerId","message");
    }
    private void due(UUID id){new TransactionTemplate(transactions).executeWithoutResult(s->ReflectionTestUtils.setField(publications.lock(id).orElseThrow(),"nextCheckAt",Instant.now().minusSeconds(1)));}
    private long product(int qty) throws Exception {return call(admin,"POST","/products",Map.of("name","Channel product","sku",UUID.randomUUID().toString(),"price",10,"quantity",qty),201).path("id").asLong();}
    private JsonNode policy(long id)throws Exception {for(JsonNode p:call(admin,"GET","/channel-stock/policies",null,200))if(p.path("productId").asLong()==id)return p;throw new AssertionError();}
    private void policy(long id,String code,int cap,int safety,boolean enabled,Long version,int expected)throws Exception {
        var body=new HashMap<String,Object>();body.put("barcode",code);body.put("maximumQuantity",cap);body.put("safetyStock",safety);body.put("enabled",enabled);body.put("version",version);
        call(admin,"PUT","/channel-stock/policies/"+id,body,expected);
    }
    private JsonNode publish(UUID id,int expected)throws Exception {return call(admin,"POST","/channel-stock/publications",Map.of("requestId",id),expected);}
    private String employee(List<String> permissions)throws Exception {
        long role=call(admin,"POST","/roles",Map.of("name",UUID.randomUUID().toString(),"scope","USER","permissions",permissions),201).path("id").asLong();
        String name="channel-"+UUID.randomUUID();JsonNode invite=call(admin,"POST","/auth/accounts",Map.of("username",name,"role","USER","employeeRoleId",role),200);
        call(null,"POST","/auth/activate",Map.of("token",invite.path("token").asText(),"password","deneme123456789"),204);return login(name,"deneme123456789");
    }
    private String login(String name,String password)throws Exception {return call(null,"POST","/auth/login",Map.of("username",name,"password",password),200).path("accessToken").asText();}
    private JsonNode call(String token,String method,String path,Object body,int expected)throws Exception {
        var request=request(org.springframework.http.HttpMethod.valueOf(method),"/api/v1"+path).contentType(MediaType.APPLICATION_JSON);
        if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.content(json.writeValueAsBytes(body));
        var result=mvc.perform(request).andReturn();assertThat(result.getResponse().getStatus()).as(method+" "+path+" "+result.getResponse().getContentAsString()).isEqualTo(expected);
        String text=result.getResponse().getContentAsString();return text.isBlank()?json.nullNode():json.readTree(text);
    }
}
