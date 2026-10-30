package com.denizcan.stockorderpayment.web.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ChannelOrderRequest(
        @NotBlank @Size(max = 128) String externalOrderId,
        @NotEmpty List<@NotNull @Valid Item> items
) {
    public record Item(@NotBlank @Size(max = 64) String sku, @NotNull @Min(1) Integer quantity) {}
}
