package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.OrderSource;
import com.denizcan.stockorderpayment.exception.*;
import com.denizcan.stockorderpayment.repository.order.OrderRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.web.order.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChannelOrderService {
    private final OrderRepository orders;
    private final ProductRepository products;
    private final OrderService orderService;
    private final PlatformTransactionManager transactionManager;

    // The facade has no ambient transaction: a unique-key collision must fully roll
    // back before the winning transaction's order can be read.
    public OrderResponse importOrder(OrderSource source, Long accountId, ChannelOrderRequest request) {
        if (source == OrderSource.INTERNAL) throw new InvalidOrderException("INTERNAL is not an external channel");
        TreeMap<String, Integer> quantities = new TreeMap<>();
        try {
            request.items().forEach(item -> quantities.merge(item.sku(), item.quantity(), Math::addExact));
        } catch (ArithmeticException ex) {
            throw new InvalidOrderException("Combined quantity exceeds the supported limit");
        }
        String fingerprint = fingerprint(quantities);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            return tx.execute(status -> {
                Optional<OrderResponse> existing = existing(source, request.externalOrderId(), fingerprint);
                if (existing.isPresent()) return existing.get();
                List<CreateOrderItemRequest> items = quantities.entrySet().stream().map(item -> {
                    Long id = products.findBySku(item.getKey()).orElseThrow(() ->
                            new InvalidOrderException("Unknown SKU: " + item.getKey())).getId();
                    return new CreateOrderItemRequest(id, item.getValue());
                }).toList();
                return orderService.createFromChannel(new CreateOrderRequest(accountId, items),
                        source, request.externalOrderId(), fingerprint);
            });
        } catch (DataIntegrityViolationException collision) {
            return tx.execute(status -> existing(source, request.externalOrderId(), fingerprint)
                    .orElseThrow(() -> collision));
        }
    }

    private Optional<OrderResponse> existing(OrderSource source, String externalId, String fingerprint) {
        return orders.findBySourceAndExternalOrderId(source, externalId).map(order -> {
            if (!Objects.equals(order.getExternalPayloadHash(), fingerprint)) throw new ExternalOrderConflictException();
            return OrderMapper.toResponse(order);
        });
    }

    private String fingerprint(TreeMap<String, Integer> quantities) {
        try {
            // Length prefixes prevent delimiter collisions in arbitrary SKU values.
            StringBuilder canonical = new StringBuilder();
            quantities.forEach((sku, qty) -> canonical.append(sku.length()).append(':').append(sku).append(':').append(qty).append(';'));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
