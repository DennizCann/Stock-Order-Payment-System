package com.denizcan.stockorderpayment.service.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** Quantity-only adapter for existing approved Trendyol listings. Never creates products. */
@Component
public class TrendyolStockGateway {
    private final RestClient client;
    private final String sellerId, connectionKey, mode;
    private final boolean enabled;
    public record Item(String barcode,int quantity) {}
    public enum Outcome { PENDING, SUCCESS, FAILED, UNCERTAIN }
    public static class Rejected extends RuntimeException { public Rejected() {super("Trendyol isteği reddetti; bağlantı bilgilerini ve yetkileri kontrol et.");} }

    @Autowired public TrendyolStockGateway(Environment environment) {
        mode=environment.getProperty("app.trendyol.environment","STAGE");
        sellerId=environment.getProperty("app.trendyol.seller-id","");
        String key=environment.getProperty("app.trendyol.api-key","");
        String secret=environment.getProperty("app.trendyol.api-secret","");
        enabled=environment.getProperty("app.trendyol.enabled",Boolean.class,false)
                && Set.of("STAGE","PROD").contains(mode) && sellerId.matches("[0-9]{1,20}") && !key.isBlank() && !secret.isBlank();
        connectionKey=digest(mode+":"+sellerId+":"+key+":"+secret);
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection,String method) throws IOException {
                super.prepareConnection(connection,method);connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(5));factory.setReadTimeout(Duration.ofSeconds(15));
        String host="PROD".equals(mode)?"https://apigw.trendyol.com":"https://stageapigw.trendyol.com";
        client=authenticated(RestClient.builder().baseUrl(host).requestFactory(factory),sellerId,key,secret);
    }
    // Test seam uses Spring's in-process HTTP contract server, never a real seller account.
    TrendyolStockGateway(RestClient client,String sellerId,String mode,String connectionKey) {
        this.client=client;this.sellerId=sellerId;this.mode=mode;this.connectionKey=connectionKey;enabled=true;
    }
    static RestClient authenticated(RestClient.Builder builder,String seller,String key,String secret) {
        return builder.defaultHeaders(h->{h.setBasicAuth(key,secret,StandardCharsets.UTF_8);h.set(HttpHeaders.USER_AGENT,seller+" - SelfIntegration");})
                .defaultStatusHandler(s->s.is3xxRedirection(),(request,response)->{throw new Rejected();}).build();
    }
    public boolean enabled() {return enabled;}
    public String connectionKey() {return connectionKey;}
    public String mode() {return mode;}
    public String sellerId() {return enabled?sellerId:"";}
    public String submit(List<Item> items) {
        if(!enabled) throw new Rejected();
        try {
            JsonNode response=client.post().uri("/integration/inventory/sellers/{id}/products/price-and-inventory",sellerId)
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("items",items)).retrieve().body(JsonNode.class);
            String batch=response==null?"":response.path("batchRequestId").asText("");
            if(!batch.matches("[A-Za-z0-9_-]{1,160}")) throw new IllegalStateException("Uncertain provider result");
            return batch;
        } catch(HttpClientErrorException rejected) {throw new Rejected();}
    }
    public Outcome check(String batch,List<Item> expected) {
        JsonNode response=client.get().uri("/integration/product/sellers/{id}/products/batch-requests/{batch}",sellerId,batch)
                .retrieve().body(JsonNode.class);
        if(response==null || !response.path("items").isArray()) return Outcome.PENDING;
        if(response.hasNonNull("batchRequestId")&&!batch.equals(response.path("batchRequestId").asText())) return Outcome.UNCERTAIN;
        Map<String,Integer> quantities=new HashMap<>();expected.forEach(i->quantities.put(i.barcode(),i.quantity()));
        Set<String> found=new HashSet<>();boolean pending=false,failed=false;
        for(JsonNode item:response.path("items")) {
            var request=item.path("requestItem");String code=request.path("barcode").asText();
            if(!quantities.containsKey(code)||!found.add(code)) return Outcome.UNCERTAIN;
            if(!request.path("quantity").isIntegralNumber() || !request.path("quantity").canConvertToInt()
                    || request.path("quantity").asInt()!=quantities.get(code)) return Outcome.UNCERTAIN;
            String status=item.path("status").asText();
            if("FAILED".equals(status)) failed=true;
            else if(!"SUCCESS".equals(status)) pending=true;
        }
        if(found.size()!=expected.size()||pending) return Outcome.PENDING;
        if(!failed&&response.path("failedItemCount").asInt(0)>0) return Outcome.UNCERTAIN;
        return failed?Outcome.FAILED:Outcome.SUCCESS;
    }
    private static String digest(String input) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception exception){throw new IllegalStateException(exception);}
    }
}
