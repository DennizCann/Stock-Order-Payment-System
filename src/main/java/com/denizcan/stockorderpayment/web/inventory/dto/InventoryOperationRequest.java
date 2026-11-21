package com.denizcan.stockorderpayment.web.inventory.dto;
import com.denizcan.stockorderpayment.domain.inventory.StockOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public record InventoryOperationRequest(@NotNull UUID requestId, @NotNull StockOperation.Kind kind,
        @Positive Long sourceBranchId, @Positive Long targetBranchId, @Positive Long typeId,
        @Size(max = 100) String label, @Size(max = 255) String note,
        @NotEmpty List<@NotNull @Valid Item> items) {
    public record Item(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) {}
}
