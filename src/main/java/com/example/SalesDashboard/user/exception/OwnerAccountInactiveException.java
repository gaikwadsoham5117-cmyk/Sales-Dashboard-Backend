package com.example.SalesDashboard.user.exception;

public class OwnerAccountInactiveException extends RuntimeException {
    public OwnerAccountInactiveException(String message) {
        super(message);
    }
}