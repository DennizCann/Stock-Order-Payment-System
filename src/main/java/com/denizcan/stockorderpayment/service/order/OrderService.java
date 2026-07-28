package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.Order;
import com.denizcan.stockorderpayment.domain.order.OrderItem;
import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.exception.InvalidOrderException;
import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.repository.order.OrderRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderItemRequest;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderRequest;
import com.denizcan.stockorderpayment.web.order.dto.OrderMapper;
import com.denizcan.stockorderpayment.web.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Map<Long, Integer> quantitiesByProductId = mergeItemQuantities(request.items());
        Map<Long, Product> productsById = loadProducts(quantitiesByProductId.keySet());

        Order order = new Order(request.userId());
        quantitiesByProductId.forEach((productId, quantity) -> {
            Product product = productsById.get(productId);
            order.addItem(new OrderItem(productId, quantity, product.getPrice()));
        });

        Order saved = orderRepository.save(order);
        log.info("Created order id={} userId={} total={} itemCount={}",
                saved.getId(), saved.getUserId(), saved.getTotalAmount(), saved.getItems().size());

        return OrderMapper.toResponse(saved);
    }

    private Map<Long, Integer> mergeItemQuantities(List<CreateOrderItemRequest> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (CreateOrderItemRequest item : items) {
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new InvalidOrderException("Quantity must be greater than zero");
            }
            if (item.productId() == null) {
                throw new InvalidOrderException("Product id is required");
            }
            merged.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return merged;
    }

    private Map<Long, Product> loadProducts(Set<Long> productIds) {
        List<Product> products = productRepository.findAllById(productIds);
        Map<Long, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (Long productId : productIds) {
            if (!byId.containsKey(productId)) {
                throw new ProductNotFoundException(productId);
            }
        }
        return byId;
    }
}
