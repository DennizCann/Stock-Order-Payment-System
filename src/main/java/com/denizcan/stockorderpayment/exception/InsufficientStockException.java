package com.denizcan.stockorderpayment.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int available, int requested) {
        super("Insufficient stock for product id " + productId
                + ". Available: " + available + ", requested: " + requested);
    }
}
