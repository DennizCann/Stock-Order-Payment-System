package com.denizcan.stockorderpayment.exception;

public class StockNotFoundException extends RuntimeException {

    public StockNotFoundException(Long productId) {
        super("Stock not found for product id: " + productId);
    }
}
