package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.Order;
import com.denizcan.stockorderpayment.domain.order.OrderItem;
import com.denizcan.stockorderpayment.domain.order.OrderSource;
import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.exception.InvalidOrderException;
import com.denizcan.stockorderpayment.exception.OrderNotFoundException;
import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.repository.order.OrderRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.service.stock.StockService;
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
    private final StockService stockService;

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        return createOrder(request, OrderSource.INTERNAL, null, null);
    }

    @Transactional
    public OrderResponse createFromChannel(CreateOrderRequest request, OrderSource source,
                                           String externalId, String payloadHash) {
        return createOrder(request, source, externalId, payloadHash);
    }

    private OrderResponse createOrder(CreateOrderRequest request, OrderSource source,
                                      String externalId, String payloadHash) {
        Map<Long, Integer> quantitiesByProductId = mergeItemQuantities(request.items());
        Map<Long, Product> productsById = loadProducts(quantitiesByProductId.keySet());

        Order order = new Order(request.userId());
        order.assignExternalReference(source, externalId, payloadHash);
        quantitiesByProductId.forEach((productId, quantity) -> {
            Product product = productsById.get(productId);
            order.addItem(new OrderItem(productId, quantity, product.getPrice()));
        });

        Order saved = orderRepository.save(order);
        if (externalId != null) orderRepository.flush();
        reserveStockForOrder(saved.getId(), quantitiesByProductId);

        log.info("Created order id={} userId={} total={} itemCount={}",
                saved.getId(), saved.getUserId(), saved.getTotalAmount(), saved.getItems().size());

        return OrderMapper.toResponse(saved);
    }

    private void reserveStockForOrder(Long orderId, Map<Long, Integer> quantitiesByProductId) {
        quantitiesByProductId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(item -> stockService.reserve(item.getKey(), item.getValue(),
                        "Reserved for order " + orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAllForUser(Long userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(OrderMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAllForOperations() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(OrderMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findByIdForUser(Long id, Long userId) {
        return orderRepository.findByIdAndUserId(id, userId).map(OrderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
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
            try {
                merged.merge(item.productId(), item.quantity(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw new InvalidOrderException("Combined item quantity exceeds the supported limit");
            }
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
