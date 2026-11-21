package com.denizcan.stockorderpayment.web.auth;

import com.denizcan.stockorderpayment.domain.user.*;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.denizcan.stockorderpayment.repository.branch.BranchRepository;
import com.denizcan.stockorderpayment.domain.branch.Branch;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SelfAccountIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired BranchRepository branches;@Autowired PasswordEncoder passwords;
    String admin;static final String ORIGINAL="OriginalPassword123!";
    @BeforeEach void setup()throws Exception{admin=login("admin","admin123");}
    @Test void ownerCanEditOwnIdentityButRenameRequiresPasswordAndRevokesEveryOldSession()throws Exception {
        String name=fixture(Role.ADMIN,null),first=login(name,ORIGINAL),second=login(name,ORIGINAL);JsonNode me=call(first,"GET","/auth/me",null,200);
        JsonNode named=call(first,"PUT","/auth/me",Map.of("revision",me.path("revision").asText(),"username",name,"fullName","Şirket sahibi"),200);
        assertThat(named.path("roleName").asText()).isEqualTo("Yönetici");assertThat(named.path("reloginRequired").asBoolean()).isFalse();
        String renamed="renamed-"+UUID.randomUUID();var body=new HashMap<String,Object>();body.put("revision",named.path("revision").asText());body.put("username",renamed);body.put("fullName","Şirket sahibi");
        call(first,"PUT","/auth/me",body,403);body.put("currentPassword",ORIGINAL);assertThat(call(first,"PUT","/auth/me",body,200).path("reloginRequired").asBoolean()).isTrue();
        denied(first);denied(second);call(null,"POST","/auth/login",Map.of("username",name,"password",ORIGINAL),401);
        assertThat(call(login(renamed,ORIGINAL),"GET","/auth/me",null,200).path("fullName").asText()).isEqualTo("Şirket sahibi");
    }
    @Test void passwordChangeRevokesAllOwnSessionsAndLeavesOtherAccountsAlone()throws Exception {
        String name=fixture(Role.USER,null),first=login(name,ORIGINAL),second=login(name,ORIGINAL);
        call(first,"POST","/auth/me/password",Map.of("currentPassword","wrong","newPassword","NewPassword123456!"),403);
        call(first,"GET","/auth/me",null,200);
        call(first,"POST","/auth/me/password",Map.of("currentPassword",ORIGINAL,"newPassword","short"),400);
        call(first,"POST","/auth/me/password",Map.of("currentPassword",ORIGINAL,"newPassword",ORIGINAL),400);
        call(first,"POST","/auth/me/password",Map.of("currentPassword",ORIGINAL,"newPassword","NewPassword123456!"),204);
        denied(first);denied(second);call(null,"POST","/auth/login",Map.of("username",name,"password",ORIGINAL),401);
        call(login(name,"NewPassword123456!"),"GET","/auth/me",null,200);call(admin,"GET","/auth/me",null,200);
    }
    @Test void branchWorkersCanManageOwnProfileButCannotEscalateRoleOrResetOthers()throws Exception {
        var branch=branches.save(new Branch("Profile branch "+UUID.randomUUID()));String name=fixture(Role.BRANCH_EMPLOYEE,branch);
        String token=login(name,ORIGINAL);var me=call(token,"GET","/auth/me",null,200);
        call(token,"PUT","/auth/me",Map.of("revision",me.path("revision").asText(),"username",name,"fullName","Şube personeli","role","ADMIN"),200);
        assertThat(users.findByUsername(name).orElseThrow().getRole()).isEqualTo(Role.BRANCH_EMPLOYEE);
        call(token,"POST","/auth/accounts/"+users.findByUsername("admin").orElseThrow().getId()+"/password-link",Map.of("revision","ignored"),403);
    }
    @Test void managerPasswordLinksRevokeOldTokensEvenAfterActivationAndOnlyNewestLinkWorks()throws Exception {
        String name=fixture(Role.USER,null),old=login(name,ORIGINAL);long id=users.findByUsername(name).orElseThrow().getId();JsonNode account=employee(id);
        var first=call(admin,"POST","/auth/accounts/"+id+"/password-link",Map.of("revision",account.path("revision").asText()),200);
        denied(old);call(null,"POST","/auth/login",Map.of("username",name,"password",ORIGINAL),401);
        var second=call(admin,"POST","/auth/accounts/"+id+"/password-link",Map.of("revision",first.path("account").path("revision").asText()),200);
        call(null,"POST","/auth/activate",Map.of("token",first.path("token").asText(),"password","ResetPassword123!"),400);
        call(null,"POST","/auth/activate",Map.of("token",second.path("token").asText(),"password","ResetPassword123!"),204);
        denied(old);call(login(name,"ResetPassword123!"),"GET","/auth/me",null,200);
        assertThat(employee(id).path("permissions")).isEqualTo(account.path("permissions"));
    }
    @Test void passwordResetRejectsStaleFormsDisabledAccountsAndOwnerTargets()throws Exception {
        String name=fixture(Role.USER,null);long id=users.findByUsername(name).orElseThrow().getId();JsonNode employee=employee(id);
        call(admin,"PUT","/auth/accounts/"+id,Map.of("revision",employee.path("revision").asText(),"username",name,"fullName","Changed","role","USER","enabled",true),200);
        call(admin,"POST","/auth/accounts/"+id+"/password-link",Map.of("revision",employee.path("revision").asText()),409);
        employee=employee(id);call(admin,"PUT","/auth/accounts/"+id,Map.of("revision",employee.path("revision").asText(),"username",name,"fullName","Changed","role","USER","enabled",false),200);
        call(admin,"POST","/auth/accounts/"+id+"/password-link",Map.of("revision",employee(id).path("revision").asText()),409);
        call(admin,"POST","/auth/accounts/"+users.findByUsername("admin").orElseThrow().getId()+"/password-link",Map.of("revision","ignored"),403);
    }
    @Test void byteLimitAndStaleIdentityFormFailWithoutChangingTheAccount()throws Exception {
        String name=fixture(Role.USER,null),token=login(name,ORIGINAL);JsonNode me=call(token,"GET","/auth/me",null,200);
        call(token,"POST","/auth/me/password",Map.of("currentPassword",ORIGINAL,"newPassword","ş".repeat(40)),400);
        var body=Map.of("revision",me.path("revision").asText(),"username",name,"fullName","First change");call(token,"PUT","/auth/me",body,200);
        call(token,"PUT","/auth/me",Map.of("revision",me.path("revision").asText(),"username",name,"fullName","Stale change"),409);
        assertThat(call(token,"GET","/auth/me",null,200).path("fullName").asText()).isEqualTo("First change");
    }
    private String fixture(Role role,Branch branch) {
        String name="self-"+UUID.randomUUID();User user=new User(name,passwords.encode(ORIGINAL),role);
        user.reviseAccount(name,null,role,branch,null,true);users.saveAndFlush(user);return name;
    }
    private JsonNode employee(long id)throws Exception{for(JsonNode e:call(admin,"GET","/auth/accounts",null,200))if(e.path("id").asLong()==id)return e;throw new AssertionError();}
    private void denied(String token)throws Exception {int status=mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus();assertThat(status).isIn(401,403);}
    private String login(String name,String password)throws Exception{return call(null,"POST","/auth/login",Map.of("username",name,"password",password),200).path("accessToken").asText();}
    private JsonNode call(String token,String method,String path,Object body,int expected)throws Exception {
        var request=request(HttpMethod.valueOf(method),"/api/v1"+path).contentType(MediaType.APPLICATION_JSON);if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.content(json.writeValueAsBytes(body));var result=mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(method+" "+path+" "+result.getResponse().getContentAsString()).isEqualTo(expected);
        String text=result.getResponse().getContentAsString();return text.isBlank()?json.nullNode():json.readTree(text);
    }
}
