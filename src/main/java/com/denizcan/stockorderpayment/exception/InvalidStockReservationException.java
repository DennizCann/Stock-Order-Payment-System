package com.denizcan.stockorderpayment.exception;

public class InvalidStockReservationException extends RuntimeException {

    public InvalidStockReservationException(String message) {
        super(message);
    }
}
