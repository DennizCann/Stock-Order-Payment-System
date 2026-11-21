package com.denizcan.stockorderpayment.web.order;
import com.denizcan.stockorderpayment.service.order.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController @RequestMapping("/api/v1/channel-stock") @RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','CHANNELS_VIEW')")
public class ChannelStockController {
    private final ChannelStockService stock;
    private final StockPublicationWorker worker;
    private final TrendyolStockGateway gateway;
    public record Connection(boolean configured,String environment,String sellerId,String message) {}
    @GetMapping("/connection") public Connection connection() {return new Connection(gateway.enabled(),gateway.mode(),gateway.sellerId(),
        gateway.enabled()?"Yalnızca mevcut Trendyol ürünlerinin stok gönderimi. Otomatik sipariş aktarımı henüz bağlı değil.":"Gerçek bağlantı kapalı; mağazaya gönderim yapılmaz. Stok ayarları hazırlanabilir.");}
    @GetMapping("/policies") public List<ChannelStockService.Policy> list() {return stock.list();}
    public record Update(@NotBlank @Size(max=40) @Pattern(regexp="[\\p{L}\\p{N}._-]+") String barcode,
                         @NotNull @Min(0) @Max(20000) Integer maximumQuantity,@NotNull @Min(0) Integer safetyStock,
                         @NotNull Boolean enabled,@PositiveOrZero Long version) {}
    @PutMapping("/policies/{productId}") @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','CHANNELS_MANAGE')")
    public void update(@PathVariable Long productId,@Valid @RequestBody Update update) {
        stock.update(productId,update.barcode(),update.maximumQuantity(),update.safetyStock(),update.enabled(),update.version());
    }
    @GetMapping("/publications") public List<ChannelStockService.Publication> history() {return stock.history();}
    public record Publish(@NotNull UUID requestId) {}
    @PostMapping("/publications") @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','CHANNELS_MANAGE')")
    public ChannelStockService.Publication publish(@Valid @RequestBody Publish request,Principal actor) {
        UUID id=stock.enqueue(request.requestId(),actor.getName());worker.process(id);return stock.get(id);
    }
    @PostMapping("/publications/{id}/check") @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','CHANNELS_MANAGE')")
    public ChannelStockService.Publication check(@PathVariable UUID id) {worker.process(id);return stock.get(id);}
    public record Resolve(@NotBlank @Size(max=80) String note,@AssertTrue boolean providerVerified) {}
    @PostMapping("/publications/{id}/resolve") @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','CHANNELS_MANAGE')")
    public void resolve(@PathVariable UUID id,@Valid @RequestBody Resolve request,Principal actor) {stock.resolve(id,actor.getName(),request.note());}
}
