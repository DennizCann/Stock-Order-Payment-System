package com.denizcan.stockorderpayment.service.stock;

import com.denizcan.stockorderpayment.domain.inventory.InventoryTransactionType;
import com.denizcan.stockorderpayment.domain.stock.Stock;
import com.denizcan.stockorderpayment.exception.InsufficientStockException;
import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.exception.StockAlreadyExistsException;
import com.denizcan.stockorderpayment.exception.StockNotFoundException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.service.inventory.InventoryTransactionService;
import com.denizcan.stockorderpayment.web.inventory.dto.StockMovementRequest;
import com.denizcan.stockorderpayment.web.stock.dto.CreateStockRequest;
import com.denizcan.stockorderpayment.web.stock.dto.StockResponse;
import com.denizcan.stockorderpayment.web.stock.dto.UpdateStockRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final InventoryTransactionService inventoryTransactionService;

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

        inventoryTransactionService.record(
                productId,
                InventoryTransactionType.INITIAL,
                0,
                saved.getQuantity(),
                "Initial stock created"
        );

        log.info("Created stock for productId={} quantity={}", productId, saved.getQuantity());
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

        int previous = stock.getQuantity();
        stock.setQuantity(request.quantity());

        inventoryTransactionService.record(
                productId,
                InventoryTransactionType.ADJUSTMENT,
                previous,
                stock.getQuantity(),
                "Manual quantity set"
        );

        log.info("Adjusted stock for productId={} from={} to={}", productId, previous, stock.getQuantity());
        return StockResponse.from(stock);
    }

    @Transactional
    public StockResponse stockIn(Long productId, StockMovementRequest request) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));

        int previous = stock.getQuantity();
        int updated = previous + request.quantity();
        stock.setQuantity(updated);

        inventoryTransactionService.record(
                productId,
                InventoryTransactionType.STOCK_IN,
                previous,
                updated,
                request.note() != null ? request.note() : "Stock in"
        );

        log.info("Stock-in productId={} amount={} newQuantity={}", productId, request.quantity(), updated);
        return StockResponse.from(stock);
    }

    @Transactional
    public StockResponse stockOut(Long productId, StockMovementRequest request) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));

        int previous = stock.getQuantity();
        if (stock.getAvailableQuantity() < request.quantity()) {
            throw new InsufficientStockException(
                    productId,
                    stock.getAvailableQuantity(),
                    request.quantity()
            );
        }

        int updated = previous - request.quantity();
        stock.setQuantity(updated);

        inventoryTransactionService.record(
                productId,
                InventoryTransactionType.STOCK_OUT,
                previous,
                updated,
                request.note() != null ? request.note() : "Stock out"
        );

        log.info("Stock-out productId={} amount={} newQuantity={}", productId, request.quantity(), updated);
        return StockResponse.from(stock);
    }

    @Transactional
    public StockResponse reserve(Long productId, int amount, String note) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));

        int previousAvailable = stock.getAvailableQuantity();
        stock.reserve(amount);

        inventoryTransactionService.record(
                productId,
                InventoryTransactionType.RESERVATION,
                previousAvailable,
                stock.getAvailableQuantity(),
                note != null ? note : "Stock reservation"
        );

        log.info(
                "Reserved stock productId={} amount={} available={}->{}",
                productId,
                amount,
                previousAvailable,
                stock.getAvailableQuantity()
        );
        return StockResponse.from(stock);
    }
}
