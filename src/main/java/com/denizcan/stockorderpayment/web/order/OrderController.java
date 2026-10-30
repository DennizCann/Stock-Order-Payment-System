package com.denizcan.stockorderpayment.web.order;

import com.denizcan.stockorderpayment.security.CurrentUserService;
import com.denizcan.stockorderpayment.service.order.OrderService;
import com.denizcan.stockorderpayment.web.order.dto.CreateOrderRequest;
import com.denizcan.stockorderpayment.web.order.dto.OrderResponse;
import com.denizcan.stockorderpayment.web.order.dto.PlaceOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @Operation(summary = "Place an order for the authenticated account")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody PlaceOrderRequest request,
                                                Principal principal) {
        Long userId = currentUserService.idForUsername(principal.getName());
        OrderResponse response = orderService.create(new CreateOrderRequest(userId, request.items()));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "List the authenticated account's orders")
    public List<OrderResponse> list(Principal principal) {
        return orderService.findAllForUser(currentUserService.idForUsername(principal.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order owned by the authenticated account")
    public OrderResponse findById(@PathVariable Long id, Principal principal) {
        return orderService.findByIdForUser(id, currentUserService.idForUsername(principal.getName()));
    }
}
