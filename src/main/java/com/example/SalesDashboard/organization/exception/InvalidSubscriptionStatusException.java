package com.example.SalesDashboard.organization.exception;

public class InvalidSubscriptionStatusException extends RuntimeException {
    public InvalidSubscriptionStatusException(String message) {
        super(message);
    }
}