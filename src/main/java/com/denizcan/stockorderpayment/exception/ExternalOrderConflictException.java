package com.denizcan.stockorderpayment.exception;

public class ExternalOrderConflictException extends RuntimeException {
    public ExternalOrderConflictException() {
        super("External order id already exists with different items");
    }
}
