package com.denizcan.stockorderpayment.web.inventory;

import com.denizcan.stockorderpayment.domain.inventory.*;
import com.denizcan.stockorderpayment.repository.inventory.StockOperationTypeRepository;
import com.denizcan.stockorderpayment.service.inventory.InventoryOperationService;
import com.denizcan.stockorderpayment.web.inventory.dto.InventoryOperationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.List;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class InventoryOperationController {
    private final InventoryOperationService operations;
    private final StockOperationTypeRepository types;
    @PostMapping("/inventory-operations") @ResponseStatus(HttpStatus.CREATED)
    public InventoryOperationService.Operation create(@Valid @RequestBody InventoryOperationRequest request, Principal principal) {
        return operations.create(principal.getName(), request);
    }
    @GetMapping("/inventory-operations")
    public List<InventoryOperationService.Operation> list(Principal principal) { return operations.list(principal.getName()); }
    @GetMapping("/inventory-operation-types") @Transactional(readOnly = true)
    public List<Type> types(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return types.findAllByOrderByNameAsc().stream().filter(t -> includeArchived || t.isActive()).map(Type::from).toList();
    }
    @PostMapping("/inventory-operation-types") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'STOCK_TYPES_MANAGE')") @Transactional
    public Type createType(@Valid @RequestBody CreateType request) {
        return Type.from(types.saveAndFlush(new StockOperationType(request.name(), request.kind())));
    }
    @PutMapping("/inventory-operation-types/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'STOCK_TYPES_MANAGE')") @Transactional
    public Type updateType(@PathVariable Long id, @Valid @RequestBody UpdateType request) {
        StockOperationType type = types.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hareket adı bulunamadı."));
        if (type.getVersion() != request.version()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Hareket adı değişmiş. Listeyi yenile.");
        type.revise(request.name(), request.kind(), request.active()); return Type.from(types.saveAndFlush(type));
    }
    public record CreateType(@NotBlank @Size(max=100) String name, @NotNull StockOperation.Kind kind) {}
    public record UpdateType(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=100) String name,
                             @NotNull StockOperation.Kind kind, @NotNull Boolean active) {}
    public record Type(Long id, String name, StockOperation.Kind kind, boolean active, long version) {
        static Type from(StockOperationType type) { return new Type(type.getId(), type.getName(), type.getKind(), type.isActive(), type.getVersion()); }
    }
}
