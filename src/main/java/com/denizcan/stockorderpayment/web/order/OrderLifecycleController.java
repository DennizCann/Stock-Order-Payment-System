package com.denizcan.stockorderpayment.web.order;
import com.denizcan.stockorderpayment.domain.order.OrderStatus;
import com.denizcan.stockorderpayment.service.order.OrderLifecycleService;
import com.denizcan.stockorderpayment.web.order.dto.OrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
@RestController @RequestMapping("/api/v1/operations/orders") @RequiredArgsConstructor
public class OrderLifecycleController {
    private final OrderLifecycleService lifecycle;
    public record Update(@NotNull OrderStatus expectedStatus,@NotNull OrderStatus status,@Size(max=255) String note) {}
    @PutMapping("/{id}/state") @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','OPERATIONS_ORDERS_MANAGE')")
    public OrderResponse update(@PathVariable Long id,@Valid @RequestBody Update request,Principal actor) {
        return lifecycle.change(id,request.expectedStatus(),request.status(),actor.getName(),request.note());
    }
    @GetMapping("/{id}/history") @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','OPERATIONS_ORDERS_VIEW')")
    public List<OrderLifecycleService.Change> history(@PathVariable Long id) {return lifecycle.history(id);}
}
