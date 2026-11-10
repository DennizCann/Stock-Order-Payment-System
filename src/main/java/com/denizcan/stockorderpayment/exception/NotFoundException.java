package com.denizcan.stockorderpayment.exception;

public class NotFoundException extends RuntimeException {

    private NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException product(Long id) {
        return new NotFoundException("Product not found with id: " + id);
    }

    public static NotFoundException stock(Long productId) {
        return new NotFoundException("Stock not found for product id: " + productId);
    }

    public static NotFoundException order(Long id) {
        return new NotFoundException("Order not found: " + id);
    }
}
