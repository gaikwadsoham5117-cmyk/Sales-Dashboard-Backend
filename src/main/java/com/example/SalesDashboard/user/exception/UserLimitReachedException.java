package com.example.SalesDashboard.user.exception;

public class UserLimitReachedException extends RuntimeException {
    public UserLimitReachedException(String message) {
        super(message);
    }
}