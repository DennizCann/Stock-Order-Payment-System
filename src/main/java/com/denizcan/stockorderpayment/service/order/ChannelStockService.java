package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.*;
import com.denizcan.stockorderpayment.repository.order.*;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ChannelStockService {
    private final ChannelStockPolicyRepository policies;
    private final ProductRepository products;
    private final StockRepository stocks;
    private final StockPublicationRepository publications;
    private final TrendyolStockGateway gateway;
    public record Policy(Long productId,String name,String sku,boolean productActive,String barcode,Integer maximumQuantity,
                         Integer safetyStock,boolean enabled,Long version,int available,int publishable) {}
    @Transactional(readOnly=true) public List<Policy> list() {
        var byProduct=policies.findAllBySourceOrderByProductIdAsc(OrderSource.TRENDYOL).stream()
                .collect(Collectors.toMap(ChannelStockPolicy::getProductId,Function.identity()));
        var inventory=stocks.findAll().stream().collect(Collectors.toMap(s->s.getProductId(),s->s.getAvailableQuantity()));
        return products.findAll().stream().filter(p->p.isActive()||byProduct.containsKey(p.getId()))
                .sorted(Comparator.comparing(p->p.getId())).map(p->{
            var policy=byProduct.get(p.getId());int available=inventory.getOrDefault(p.getId(),0);
            return new Policy(p.getId(),p.getName(),p.getSku(),p.isActive(),policy==null?null:policy.getExternalCode(),
                    policy==null?null:policy.getMaximumQuantity(),policy==null?null:policy.getSafetyStock(),
                    policy!=null&&policy.isEnabled(),policy==null?null:policy.getVersion(),available,
                    policy==null?0:policy.publishable(available,p.isActive()));
        }).toList();
    }
    @Transactional public void update(Long productId,String barcode,int maximum,int safety,boolean enabled,Long version) {
        var product=products.findById(productId).orElseThrow(()->conflict("Ürün bulunamadı."));
        if(!product.isActive()&&enabled) throw conflict("Arşivlenmiş ürün satışa açılamaz.");
        ChannelStockPolicy policy=policies.findBySourceAndProductId(OrderSource.TRENDYOL,productId).orElse(null);
        if(policy==null&&version!=null || policy!=null&&!Objects.equals(policy.getVersion(),version)) throw conflict("Ayar başka bir işlemde değişmiş. Listeyi yenile.");
        if(policy==null) policy=new ChannelStockPolicy(OrderSource.TRENDYOL,productId);
        // Changing an established barcode can leave the old listing for sale. Zero it first in the provider panel.
        if(policy.getId()!=null&&!policy.getExternalCode().equals(barcode.strip())) throw conflict("Kayıtlı barkod burada değiştirilemez; önce eski mağaza kaydını uzlaştırmak gerekir.");
        policy.revise(barcode,maximum,safety,enabled);policies.saveAndFlush(policy);
    }
    @Transactional public UUID enqueue(UUID requestId,String username) {
        var existing=publications.findById(requestId);
        if(existing.isPresent()) {
            if(!existing.get().getUsername().equals(username)||!existing.get().getConnectionKey().equals(gateway.connectionKey())) throw conflict("İşlem numarası başka bir gönderime ait.");
            return requestId;
        }
        if(!gateway.enabled()) throw conflict("Trendyol bağlantısı yapılandırılmamış veya kapalı. Henüz gönderim yapılmadı.");
        Map<String,Integer> previous=new HashMap<>();
        publications.confirmedQuantities(gateway.connectionKey()).forEach(row->previous.put((String)row[0],((Number)row[1]).intValue()));
        var lines=list().stream().filter(p->p.barcode()!=null)
                .filter(p->!Objects.equals(previous.get(p.barcode()),p.publishable()))
                .map(p->new StockPublicationLine(p.productId(),p.barcode(),p.publishable())).toList();
        if(lines.isEmpty()) throw conflict("Gönderilecek değişiklik yok. Önce barkod ve stok sınırı tanımla.");
        if(lines.size()>1000) throw conflict("Bir gönderim en fazla 1000 ürün içerebilir.");
        String fingerprint=payloadHash(lines);
        if(publications.existsByConnectionKeyAndFingerprintAndCreatedAtAfter(gateway.connectionKey(),fingerprint,java.time.Instant.now().minusSeconds(901)))
            throw conflict("Aynı stok isteği son 15 dakikada oluşturulmuş. Önce gönderim sonucunu kontrol et.");
        publications.saveAndFlush(new StockPublication(requestId,gateway.connectionKey(),fingerprint,username,lines));return requestId;
    }
    public record Line(Long productId,String barcode,int quantity) {}
    public record Publication(UUID id,String state,String batchId,String message,String username,java.time.Instant createdAt,List<Line> lines) {}
    @Transactional(readOnly=true) public List<Publication> history() {
        var ids=publications.recentIds(org.springframework.data.domain.PageRequest.of(0,20));
        return ids.isEmpty()?List.of():publications.findAllByIdInOrderByCreatedAtDesc(ids).stream().map(ChannelStockService::view).toList();
    }
    @Transactional(readOnly=true) public Publication get(UUID id) {return view(publications.findById(id).orElseThrow(()->conflict("Gönderim bulunamadı.")));}
    @Transactional public void resolve(UUID id,String actor,String note) {
        var publication=publications.lock(id).orElseThrow(()->conflict("Gönderim bulunamadı."));
        if(publication.getState()!=StockPublication.State.UNCERTAIN) throw conflict("Yalnızca belirsiz gönderim uzlaştırılabilir.");
        publication.finish(StockPublication.State.FAILED,"Mağazada kontrol edilerek kapatıldı: "+actor+" · "+note.strip());
    }
    private static Publication view(StockPublication p) {return new Publication(p.getId(),p.getState().name(),p.getBatchId(),p.getMessage(),p.getUsername(),p.getCreatedAt(),
            p.getLines().stream().map(l->new Line(l.getProductId(),l.getBarcode(),l.getQuantity())).toList());}
    private static String payloadHash(List<StockPublicationLine> lines) {
        try {
            StringBuilder payload=new StringBuilder();lines.forEach(l->payload.append(l.getBarcode().length()).append(':').append(l.getBarcode()).append(':').append(l.getQuantity()).append(';'));
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }catch(Exception exception){throw new IllegalStateException(exception);}
    }
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
