package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.StockPublication;
import com.denizcan.stockorderpayment.repository.order.StockPublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.*;
import static com.denizcan.stockorderpayment.domain.order.StockPublication.State.*;

@Service @RequiredArgsConstructor
public class StockPublicationWorker {
    private final StockPublicationRepository publications;
    private final PlatformTransactionManager transactions;
    private final TrendyolStockGateway gateway;
    private final ChannelStockService settings;
    private record Task(UUID id,boolean submit,String batch,List<TrendyolStockGateway.Item> items) {}

    @Scheduled(fixedDelayString="${app.trendyol.check-interval-ms:15000}",initialDelayString="${app.trendyol.check-interval-ms:15000}")
    public void recover() {
        // No automatic stock publication: this only finishes explicitly requested durable jobs.
        for(UUID id:publications.activeIds()) {try {process(id);}catch(RuntimeException ignored){/* persisted job remains retryable */}}
    }
    public void process(UUID id) {
        var tx=new TransactionTemplate(transactions);
        Task task=tx.execute(status->{
            var p=publications.lock(id).orElse(null);if(p==null||p.getActiveKey()==null) return null;
            if(!gateway.enabled()||!p.getConnectionKey().equals(gateway.connectionKey())) {
                p.finish(UNCERTAIN,"Bağlantı değişmiş veya kapalı. Önce eski mağaza gönderimini uzlaştır.");return null;
            }
            if(p.getState()==SENDING) {
                if(p.getLastCheckedAt().isBefore(Instant.now().minusSeconds(90)))
                    p.finish(UNCERTAIN,"Gönderimin sonucu alınamadı. Otomatik tekrar gönderilmeyecek; mağazadan kontrol gerekiyor.");
                return null;
            }
            if(p.getState()==UNCERTAIN) return null;
            boolean submit=p.getState()==QUEUED;
            if(!submit&&p.getState()!=SUBMITTED) return null;
            if(submit) {
                var current=new HashMap<Long,ChannelStockService.Policy>();
                settings.list().forEach(policy->current.put(policy.productId(),policy));
                boolean changed=p.getLines().stream().anyMatch(line->{
                    var policy=current.get(line.getProductId());
                    return policy==null||!Objects.equals(policy.barcode(),line.getBarcode())||policy.publishable()!=line.getQuantity();
                });
                if(changed) {
                    p.finish(FAILED,"Bekleyen gönderimden sonra stok veya ayarlar değişmiş. Güncel önizlemeyle yeni gönderim oluştur.");
                    return null;
                }
            }
            if(!submit&&p.getCreatedAt().isBefore(Instant.now().minusSeconds(3*3600))) {
                p.finish(UNCERTAIN,"Sonuç uzun süredir doğrulanamadı. Mağazadan kontrol gerekiyor.");return null;
            }
            if(!submit&&p.getNextCheckAt()!=null&&p.getNextCheckAt().isAfter(Instant.now())) return null;
            if(submit) p.sending();else p.pending();
            return new Task(id,submit,p.getBatchId(),p.getLines().stream().map(l->new TrendyolStockGateway.Item(l.getBarcode(),l.getQuantity())).toList());
        });
        if(task==null) return;
        if(task.submit()) {
            try {String batch=gateway.submit(task.items());finish(tx,id,SENDING,p->p.submitted(batch));}
            catch(TrendyolStockGateway.Rejected rejected) {finish(tx,id,SENDING,p->p.finish(FAILED,rejected.getMessage()));}
            catch(RuntimeException unknown) {finish(tx,id,SENDING,p->p.finish(UNCERTAIN,"Gönderim sonucu belirsiz. Yeniden göndermeden önce mağazadan kontrol et."));}
        } else {
            try {
                var result=gateway.check(task.batch(),task.items());
                finish(tx,id,SUBMITTED,p->{switch(result) {
                    case SUCCESS -> p.finish(SUCCESS,"Tüm ürünlerin stok sonucu Trendyol tarafından onaylandı.");
                    case FAILED -> p.finish(FAILED,"Bir veya daha fazla ürün reddedildi. Ayrıntıyı mağaza panelinden kontrol et.");
                    case UNCERTAIN -> p.finish(UNCERTAIN,"Dönen ürünler gönderimle eşleşmiyor. Mağazadan kontrol gerekiyor.");
                    case PENDING -> p.pending();
                }});
            }catch(RuntimeException unavailable){/* nextCheckAt was persisted before HTTP; keep SUBMITTED and retry later */}
        }
    }
    private void finish(TransactionTemplate tx,UUID id,StockPublication.State expected,java.util.function.Consumer<StockPublication> update) {
        tx.executeWithoutResult(status->publications.lock(id).filter(p->p.getState()==expected).ifPresent(update));
    }
}
