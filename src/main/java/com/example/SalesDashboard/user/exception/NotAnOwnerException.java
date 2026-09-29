package com.example.SalesDashboard.user.exception;

public class NotAnOwnerException extends RuntimeException {
    public NotAnOwnerException(String message) {
        super(message);
    }
}