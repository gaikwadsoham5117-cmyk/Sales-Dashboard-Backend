package com.example.SalesDashboard.user.exception;

public class NoValidFieldsProvidedException extends RuntimeException {
    public NoValidFieldsProvidedException(String message) {
        super(message);
    }
}