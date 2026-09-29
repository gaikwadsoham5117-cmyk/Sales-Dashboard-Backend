package com.example.SalesDashboard.user.exception;

public class InvalidUserLimitConfiguredException extends RuntimeException {
    public InvalidUserLimitConfiguredException(String message) {
        super(message);
    }
}