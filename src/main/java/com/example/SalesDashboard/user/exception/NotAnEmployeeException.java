package com.example.SalesDashboard.user.exception;

public class NotAnEmployeeException extends RuntimeException {
    public NotAnEmployeeException(String message) {
        super(message);
    }
}