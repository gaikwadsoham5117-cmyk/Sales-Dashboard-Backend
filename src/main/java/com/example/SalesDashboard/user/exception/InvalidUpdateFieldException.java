package com.example.SalesDashboard.user.exception;

public class InvalidUpdateFieldException extends RuntimeException {
    public InvalidUpdateFieldException(String message) {
        super(message);
    }
}