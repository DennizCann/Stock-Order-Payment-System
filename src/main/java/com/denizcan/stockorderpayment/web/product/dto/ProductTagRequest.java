package com.denizcan.stockorderpayment.web.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductTagRequest(@NotBlank @Size(max = 100) String name,
                                @NotBlank @Size(max = 255) String value) {}
