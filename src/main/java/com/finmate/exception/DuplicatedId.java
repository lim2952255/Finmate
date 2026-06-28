package com.finmate.exception;

public class DuplicatedId extends RuntimeException {
    public DuplicatedId(String message) {
        super(message);
    }
}
