package com.example.productionmvp.exception;

public class PostCapacityExceededException extends RuntimeException {
    public PostCapacityExceededException(String message) {
        super(message);
    }
}
