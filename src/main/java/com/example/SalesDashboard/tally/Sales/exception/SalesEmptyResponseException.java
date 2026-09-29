package com.example.SalesDashboard.tally.Sales.exception;

public class SalesEmptyResponseException extends RuntimeException {
    public SalesEmptyResponseException(String message) {
        super(message);
    }
}