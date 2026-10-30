package com.denizcan.demo.channel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController
public class ChannelController {
    private final String source;
    private final Map<String, Inventory> inventory = new TreeMap<>();
    private final Map<String, ChannelOrder> orders = new LinkedHashMap<>();
    public ChannelController(@Value("${demo.source}") String source) { this.source = source; }
    public record Inventory(@NotBlank String sku, String name, BigDecimal price, @Min(0) int availableQuantity, long version) {}
    public record Item(@NotBlank String sku, @Min(1) int quantity) {}
    public record NewOrder(@Size(max=128) String externalOrderId, @NotEmpty List<@NotNull @Valid Item> items) {}
    public record ChannelOrder(String externalOrderId, List<Item> items, Instant createdAt, boolean imported) {}

    @GetMapping("/api/config") public Map<String,String> config() {
        return Map.of("source", source, "notice", "Independent demo simulator; not a real marketplace API");
    }
    @PutMapping("/api/inventory") public synchronized void inventory(@Valid @RequestBody List<@Valid Inventory> items) {
        items.forEach(item -> inventory.compute(item.sku(), (sku, current) ->
                current == null || item.version() >= current.version() ? item : current));
    }
    @GetMapping("/api/inventory") public synchronized List<Inventory> inventory() {
        return inventory.values().stream().map(item -> new Inventory(item.sku(), item.name(), item.price(),
                Math.max(0, item.availableQuantity() - pendingQuantity(item.sku())), item.version())).toList();
    }
    @GetMapping("/api/orders") public synchronized List<ChannelOrder> pending() {
        return orders.values().stream().filter(order -> !order.imported()).toList();
    }
    @GetMapping("/api/history") public synchronized List<ChannelOrder> history() { return List.copyOf(orders.values()); }
    @PostMapping("/api/orders") public synchronized ChannelOrder create(@Valid @RequestBody NewOrder request) {
        String id = request.externalOrderId() == null || request.externalOrderId().isBlank()
                ? UUID.randomUUID().toString() : request.externalOrderId();
        if (orders.containsKey(id)) {
            ChannelOrder existing = orders.get(id);
            if (!existing.items().equals(request.items())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order id reused");
            return existing;
        }
        Map<String,Integer> quantities = new TreeMap<>();
        try { request.items().forEach(item -> quantities.merge(item.sku(), item.quantity(), Math::addExact)); }
        catch (ArithmeticException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity overflow"); }
        quantities.forEach((sku, quantity) -> {
            Inventory item = inventory.get(sku);
            if (item == null || item.availableQuantity() - pendingQuantity(sku) < quantity)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient cached stock: " + sku);
        });
        ChannelOrder order = new ChannelOrder(id, List.copyOf(request.items()), Instant.now(), false);
        orders.put(id, order);
        return order;
    }
    @PostMapping("/api/orders/{id}/ack") public synchronized void acknowledge(@PathVariable String id,
            @Valid @RequestBody List<@Valid Inventory> snapshot) {
        ChannelOrder order = orders.get(id);
        if (order == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order missing");
        orders.put(id, new ChannelOrder(id, order.items(), order.createdAt(), true));
        inventory(snapshot);
    }
    private int pendingQuantity(String sku) {
        return orders.values().stream().filter(order -> !order.imported()).flatMap(order -> order.items().stream())
                .filter(item -> item.sku().equals(sku)).mapToInt(Item::quantity).sum();
    }
}
