package com.example.productionmvp.exception;

public class InsufficientMaterialException extends RuntimeException {
    public InsufficientMaterialException(String message) {
        super(message);
    }
}
