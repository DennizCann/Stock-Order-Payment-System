package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.OrderSource;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.web.order.dto.ChannelOrderRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Service
public class ChannelConnectorService {
    private final ChannelOrderService orders;
    private final ProductRepository products;
    private final StockRepository stocks;
    private final Map<OrderSource, RestClient> clients = new EnumMap<>(OrderSource.class);
    public record InventorySnapshot(String sku, String name, BigDecimal price, int availableQuantity, long version) {}
    public record Result(OrderSource source, boolean success, int imported, String message) {}

    public ChannelConnectorService(ChannelOrderService orders, ProductRepository products,
                                    StockRepository stocks, Environment environment) {
        this.orders = orders; this.products = products; this.stocks = stocks;
        int port = 8081;
        for (OrderSource source : List.of(OrderSource.TRENDYOL, OrderSource.HEPSIBURADA, OrderSource.N11,
                OrderSource.WEBSTORE, OrderSource.BRANCH)) {
            String url = environment.getProperty("app.channels." + source.name().toLowerCase(Locale.ROOT),
                    "http://127.0.0.1:" + port++);
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(1));
            factory.setReadTimeout(Duration.ofSeconds(2));
            clients.put(source, RestClient.builder().baseUrl(url).requestFactory(factory).build());
        }
    }

    public List<Result> synchronizeStock() {
        List<InventorySnapshot> inventory = snapshot();
        List<Result> results = new ArrayList<>();
        clients.forEach((source, client) -> {
            try {
                client.put().uri("/api/inventory").body(inventory).retrieve().toBodilessEntity();
                results.add(new Result(source, true, 0, "Stock snapshot synchronized"));
            } catch (RuntimeException ex) {
                results.add(new Result(source, false, 0, "Channel unavailable; retry stock synchronization"));
            }
        });
        return results;
    }

    public List<Result> importPending(Long accountId) {
        List<Result> results = new ArrayList<>();
        clients.forEach((source, client) -> {
            int imported = 0;
            List<String> failures = new ArrayList<>();
            try {
                ChannelOrderRequest[] pending = client.get().uri("/api/orders").retrieve().body(ChannelOrderRequest[].class);
                if (pending != null) for (ChannelOrderRequest request : pending) {
                    try {
                        // External HTTP data requires validation just like a public request.
                        validateExternalOrder(request);
                        orders.importOrder(source, accountId, request);
                        client.post().uri("/api/orders/{id}/ack", request.externalOrderId())
                                .body(snapshot()).retrieve().toBodilessEntity();
                        imported++;
                    } catch (RuntimeException ex) {
                        failures.add("Order could not be imported or acknowledged; it remains available for retry");
                    }
                }
                results.add(new Result(source, failures.isEmpty(), imported,
                        failures.isEmpty() ? "Pending orders processed" : failures.size() + " order(s) require attention"));
            } catch (RuntimeException ex) {
                results.add(new Result(source, false, imported, "Channel unavailable; retry import"));
            }
        });
        return results;
    }

    private List<InventorySnapshot> snapshot() {
        return products.findAll().stream().map(product -> {
            var stock = stocks.findByProductId(product.getId());
            return new InventorySnapshot(product.getSku(), product.getName(), product.getPrice(),
                    stock.map(value -> value.getAvailableQuantity()).orElse(0),
                    stock.map(value -> value.getVersion()).orElse(0L));
        }).toList();
    }

    private void validateExternalOrder(ChannelOrderRequest request) {
        if (request == null || request.externalOrderId() == null || request.externalOrderId().isBlank()
                || request.externalOrderId().length() > 128 || request.items() == null || request.items().isEmpty())
            throw new IllegalArgumentException("Invalid external order");
        for (ChannelOrderRequest.Item item : request.items())
            if (item == null || item.sku() == null || item.sku().isBlank() || item.sku().length() > 64
                    || item.quantity() == null || item.quantity() < 1)
                throw new IllegalArgumentException("Invalid external item");
    }
}
