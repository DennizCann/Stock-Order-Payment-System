package com.denizcan.stockorderpayment.web.stock;

import com.denizcan.stockorderpayment.service.stock.StockService;
import com.denizcan.stockorderpayment.web.stock.dto.CreateStockRequest;
import com.denizcan.stockorderpayment.web.stock.dto.StockResponse;
import com.denizcan.stockorderpayment.web.stock.dto.UpdateStockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{productId}/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockResponse create(
            @PathVariable Long productId,
            @Valid @RequestBody CreateStockRequest request
    ) {
        return stockService.create(productId, request);
    }

    @GetMapping
    public StockResponse findByProductId(@PathVariable Long productId) {
        return stockService.findByProductId(productId);
    }

    @PatchMapping
    public StockResponse updateQuantity(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateStockRequest request
    ) {
        return stockService.updateQuantity(productId, request);
    }
}
