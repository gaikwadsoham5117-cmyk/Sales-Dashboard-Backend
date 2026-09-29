package com.example.SalesDashboard.tally.Sales.exception;

public class SalesInvalidResponseException extends RuntimeException {
    public SalesInvalidResponseException(String message) {
        super(message);
    }
}