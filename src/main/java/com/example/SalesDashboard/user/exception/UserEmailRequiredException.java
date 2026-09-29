package com.example.SalesDashboard.user.exception;

public class UserEmailRequiredException extends RuntimeException {
    public UserEmailRequiredException(String message) {
        super(message);
    }
}