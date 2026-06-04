package com.denizcan.stockorderpayment.exception;

public class StockAlreadyExistsException extends RuntimeException {

    public StockAlreadyExistsException(Long productId) {
        super("Stock already exists for product id: " + productId);
    }
}
