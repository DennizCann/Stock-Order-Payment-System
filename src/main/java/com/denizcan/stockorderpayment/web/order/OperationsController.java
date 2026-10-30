package com.denizcan.stockorderpayment.web.order;

import com.denizcan.stockorderpayment.domain.order.OrderSource;
import com.denizcan.stockorderpayment.security.CurrentUserService;
import com.denizcan.stockorderpayment.service.order.ChannelOrderService;
import com.denizcan.stockorderpayment.service.order.ChannelConnectorService;
import com.denizcan.stockorderpayment.service.order.OrderService;
import com.denizcan.stockorderpayment.web.order.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OperationsController {
    private final OrderService orders;
    private final ChannelOrderService channels;
    private final CurrentUserService currentUser;
    private final ChannelConnectorService connector;

    @PostMapping("/channels/import")
    public List<ChannelConnectorService.Result> importPending(Principal principal) {
        return connector.importPending(currentUser.idForUsername(principal.getName()));
    }

    @PostMapping("/channels/sync-stock")
    public List<ChannelConnectorService.Result> synchronizeStock() {
        return connector.synchronizeStock();
    }

    @GetMapping("/orders")
    public List<OrderResponse> list() { return orders.findAllForOperations(); }

    @PostMapping("/channels/{source}/orders")
    public OrderResponse importOrder(@PathVariable OrderSource source,
            @Valid @RequestBody ChannelOrderRequest request, Principal principal) {
        return channels.importOrder(source, currentUser.idForUsername(principal.getName()), request);
    }
}
