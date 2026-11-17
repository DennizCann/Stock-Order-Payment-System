package com.denizcan.stockorderpayment.web.branch;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import com.denizcan.stockorderpayment.repository.branch.BranchRepository;
import com.denizcan.stockorderpayment.service.branch.BranchInventoryService;
import com.denizcan.stockorderpayment.service.branch.BranchInventoryService.Movement;
import com.denizcan.stockorderpayment.service.branch.BranchInventoryService.StockLine;
import com.denizcan.stockorderpayment.web.stock.dto.StockQuantityRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchRepository branches;
    private final BranchInventoryService inventory;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<BranchResponse> list() {
        return branches.findAllByOrderByNameAsc().stream().map(BranchResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponse create(@Valid @RequestBody BranchRequest request) {
        try {
            return BranchResponse.from(branches.saveAndFlush(new Branch(request.name().strip())));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu şube adı zaten kullanımda.", exception);
        }
    }

    @GetMapping("/{branchId}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StockLine> stockAt(@PathVariable Long branchId) {
        return inventory.stockAt(branchId);
    }

    @PutMapping("/{branchId}/stock/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public StockLine setQuantity(@PathVariable Long branchId, @PathVariable Long productId,
                                 @Valid @RequestBody StockQuantityRequest request, Principal principal) {
        return inventory.setQuantity(branchId, productId, request.quantity(), principal.getName());
    }

    @GetMapping("/movements")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Movement> movements() {
        return inventory.recentMovements();
    }

    @GetMapping("/me/stock")
    @PreAuthorize("hasRole('BRANCH_EMPLOYEE')")
    public List<StockLine> employeeStock(Principal principal) {
        return inventory.employeeStock(principal.getName());
    }

    @PostMapping("/me/sales")
    @PreAuthorize("hasRole('BRANCH_EMPLOYEE')")
    public Movement sale(@Valid @RequestBody SaleRequest request, Principal principal) {
        return inventory.recordSale(principal.getName(), request.productId(), request.quantity(), request.saleId());
    }

    public record SaleRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity,
                              @NotNull UUID saleId) {}

    public record BranchRequest(@NotBlank @Size(max = 100) String name) {}

    public record BranchResponse(Long id, String name) {
        static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getId(), branch.getName());
        }
    }
}
