package com.denizcan.stockorderpayment.web.product.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record UpdateProductRequest(@NotBlank @Size(max=255) String name,@NotBlank @Size(max=64) String sku,
        @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal price,
        List<@NotNull @Valid ProductTagRequest> tags,@NotNull @PositiveOrZero Long version,
        @Null(message="Adedi değiştirmek için stok hareketi oluştur.") Integer quantity) {
    public UpdateProductRequest { name=name==null?null:name.strip();sku=sku==null?null:sku.strip(); }
}
