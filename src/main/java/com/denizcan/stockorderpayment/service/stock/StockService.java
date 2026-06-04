package com.denizcan.stockorderpayment.service.stock;

import com.denizcan.stockorderpayment.domain.stock.Stock;
import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.exception.StockAlreadyExistsException;
import com.denizcan.stockorderpayment.exception.StockNotFoundException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.web.stock.dto.CreateStockRequest;
import com.denizcan.stockorderpayment.web.stock.dto.StockResponse;
import com.denizcan.stockorderpayment.web.stock.dto.UpdateStockRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    @Transactional
    public StockResponse create(Long productId, CreateStockRequest request) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        if (stockRepository.existsByProductId(productId)) {
            throw new StockAlreadyExistsException(productId);
        }

        Stock stock = new Stock(productId, request.quantity());
        Stock saved = stockRepository.save(stock);
        return StockResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public StockResponse findByProductId(Long productId) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
        return StockResponse.from(stock);
    }

    @Transactional
    public StockResponse updateQuantity(Long productId, UpdateStockRequest request) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));

        stock.setQuantity(request.quantity());
        return StockResponse.from(stock);
    }
}
