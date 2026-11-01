package com.brewpos.exception;

/** Duplicate names, category still in use, etc. -> HTTP 409. */
public class ConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
