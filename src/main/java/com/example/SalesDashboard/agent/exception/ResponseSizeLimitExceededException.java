package com.example.SalesDashboard.agent.exception;

public class ResponseSizeLimitExceededException extends RuntimeException {
    public ResponseSizeLimitExceededException(String message) {
        super(message);
    }
}
