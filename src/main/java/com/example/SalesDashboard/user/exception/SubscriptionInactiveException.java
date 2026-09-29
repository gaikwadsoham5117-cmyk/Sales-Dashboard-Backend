package com.example.SalesDashboard.user.exception;

public class SubscriptionInactiveException extends RuntimeException {
    public SubscriptionInactiveException(String message) {
        super(message);
    }
}