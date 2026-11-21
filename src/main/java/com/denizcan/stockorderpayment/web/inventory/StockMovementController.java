package com.denizcan.stockorderpayment.web.inventory;

import com.denizcan.stockorderpayment.service.inventory.StockMovementService;
import com.denizcan.stockorderpayment.service.inventory.StockMovementService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stock-movements")
@RequiredArgsConstructor
public class StockMovementController {
    private final StockMovementService movements;

    @GetMapping public List<Movement> list(Principal principal) { return movements.list(principal.getName()); }
    @GetMapping("/locations") public List<Location> locations(Principal principal) { return movements.locations(principal.getName()); }
    @GetMapping("/products") public List<ProductChoice> products(Principal principal) { return movements.products(principal.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody MovementRequest request, Principal principal) {
        movements.create(principal.getName(), request.productId(), request.branchId(), request.direction(),
                request.quantity(), request.reason(), request.note());
    }

    public record MovementRequest(@NotNull @Positive Long productId, @Positive Long branchId,
                                  @NotNull Direction direction, @NotNull @Positive Integer quantity,
                                  @Size(max = 100) String reason, @Size(max = 255) String note) {}
}
