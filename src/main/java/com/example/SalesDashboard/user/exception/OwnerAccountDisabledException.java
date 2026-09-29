package com.example.SalesDashboard.user.exception;

public class OwnerAccountDisabledException extends RuntimeException {
    public OwnerAccountDisabledException(String message) {
        super(message);
    }
}