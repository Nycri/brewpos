package com.brewpos.exception;

/** Invalid business input (bad payment method, inactive item, ...) -> HTTP 400. */
public class BusinessRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
